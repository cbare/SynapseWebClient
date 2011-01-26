package org.sagebionetworks.repo.model.gaejdo;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.jdo.JDOObjectNotFoundException;
import javax.jdo.PersistenceManager;
import javax.jdo.Query;
import javax.jdo.Transaction;

import org.sagebionetworks.repo.model.BaseDAO;
import org.sagebionetworks.repo.model.DatastoreException;
import org.sagebionetworks.repo.model.InvalidModelException;
import org.sagebionetworks.repo.web.NotFoundException;

import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;

/**
 * This class contains helper methods for DAOs.  Since each DAO may need to pick and choose
 * methods from various helpers, the chosen design pattern for the DAOs was that of a wrapper 
 * or adapter, rather than of a base class with extensions.  
 * 
 * This class is parameterized by an (implementation independent) DTO type and a 
 * JDO specific JDO type.  It's the DAO's job to translate between these types as
 * it persists and retrieves data.
 * 
 * @author bhoff
 * 
 * @param <S>
 *            the DTO class
 * @param <T>
 *            the JDO class
 */
abstract public class GAEJDOBaseDAOHelper<S, T extends GAEJDOBase> {

	/**
	 * Create a new instance of the data transfer object.  
	 * Introducing this abstract method helps us avoid making assumptions about constructors.
	 * @return the new object
	 */
	abstract public S newDTO();

	/**
	 * Create a new instance of the persistable object.
	 * Introducing this abstract method helps us avoid making assumptions about constructors.
	 * @return the new object
	 */
	abstract public T newJDO();

	/**
	 * Do a shallow copy from the JDO object to the DTO object.
	 * 
	 * @param jdo
	 * @param dto
	 */
	abstract public void copyToDto(T jdo, S dto);

	/**
	 * Do a shallow copy from the DTO object to the JDO object.
	 * 
	 * @param dto
	 * @param jdo
	 * @throws InvalidModelException
	 */
	abstract public void copyFromDto(S dto, T jdo) throws InvalidModelException;

	/**
	 * @param jdoClass
	 *            the class parameterized by T
	 */
	abstract public Class<T> getJdoClass();

	/**
	 * Create a new object, using the information in the passed DTO
	 * @param dto
	 * @return the ID of the created object
	 * @throws InvalidModelException
	 */
	public String create(S dto) throws InvalidModelException, DatastoreException {
		T jdo = newJDO();
		copyFromDto(dto, jdo);
		PersistenceManager pm = PMF.get();
		Transaction tx = null;
		try {
			tx = pm.currentTransaction();
			tx.begin();
			pm.makePersistent(jdo);
			tx.commit();
			return KeyFactory.keyToString(jdo.getId());
		} catch (Exception e) {
			throw new DatastoreException(e);
		} finally {
			if (tx.isActive()) {
				tx.rollback();
			}
			pm.close();
		}
	}

	/**
	 * 
	 * @param id id of the object to be retrieved
	 * @return the DTO version of the retrieved object
	 * @throws DatastoreException
	 * @throws NotFoundException
	 */
	public S get(String id) throws DatastoreException, NotFoundException {
		PersistenceManager pm = PMF.get();
		try {
			Key key = KeyFactory.stringToKey(id);
			@SuppressWarnings("unchecked")
			T jdo = (T) pm.getObjectById(getJdoClass(), key);
			S dto = newDTO();
			copyToDto(jdo, dto);
			pm.close();
			return dto;
		} catch (JDOObjectNotFoundException e) {
			throw new NotFoundException(e);
		} catch (Exception e) {
			throw new DatastoreException(e);
		}
	}

	/**
	 * Delete the specified object
	 * @param id the id of the object to be deleted
	 * @throws DatastoreException
	 * @throws NotFoundException
	 */
	public void delete(String id) throws DatastoreException, NotFoundException {
		PersistenceManager pm = PMF.get();
		Transaction tx = null;
		try {
			tx = pm.currentTransaction();
			tx.begin();
			Key key = KeyFactory.stringToKey(id);
			@SuppressWarnings("unchecked")
			T jdo = (T) pm.getObjectById(getJdoClass(), key);
			pm.deletePersistent(jdo);
			tx.commit();
		} catch (JDOObjectNotFoundException e) {
			throw new NotFoundException(e);
		} catch (Exception e) {
			throw new DatastoreException(e);
		} finally {
			if (tx.isActive()) {
				tx.rollback();
			}
			pm.close();
		}
	}

	/**
	 * Retrieve all objects of the given type, 'paginated' by the given start and end
	 * @param start
	 * @param end
	 * @return a subset of the results, starting at index 'start' and not going
	 *         beyond index 'end'
	 */
	public List<S> getInRange(int start, int end) throws DatastoreException  {
		PersistenceManager pm = PMF.get();
		try {
			Query query = pm.newQuery(getJdoClass());
			query.setRange(start, end);
			@SuppressWarnings("unchecked")
			List<T> list = ((List<T>) query.execute());
			List<S> ans = new ArrayList<S>();
			for (T jdo : list) {
				S dto = newDTO();
				copyToDto(jdo, dto);
				ans.add(dto);
			}
			return ans;
		} catch (Exception e) {
			throw new DatastoreException(e);
		} finally {
			pm.close();
		}
	}

	/**
	 * Retrieve all objects of the given type, 'paginated' by the given start and end and sorted 
	 * by the specified primary field
	 * @param start
	 * @param end
	 * @param sortBy
	 * @param asc
	 *            if true then ascending, else descending
	 * @return a subset of the results, starting at index 'start' and not going
	 *         beyond index 'end' and sorted by the given primary field
	 */
	public List<S> getInRangeSortedByPrimaryField(int start, int end,
			String sortBy, boolean asc) throws DatastoreException {
		PersistenceManager pm = null;
		try {
			pm = PMF.get();
			Query query = pm.newQuery(getJdoClass());
			query.setRange(start, end);
			query.setOrdering(sortBy + (asc ? " ascending" : " descending"));
			@SuppressWarnings("unchecked")
			List<T> list = ((List<T>) query.execute());
			List<S> ans = new ArrayList<S>();
			for (T jdo : list) {
				S dto = newDTO();
				copyToDto(jdo, dto);
				ans.add(dto);
			}
			return ans;
		} catch (Exception e) {
			throw new DatastoreException(e);
		} finally {
			pm.close();
		}

	}

	/**
	 * Get the objects of the given type having the specified value in the given primary field,
	 * and 'paginated' by the given start/end limits
	 * @param start
	 * @param end
	 * @param attribute the name of the primary field
	 * @param value
	 * @return
	 */
	public List<S> getInRangeHavingPrimaryField(int start, int end,
			String attribute, Object value) throws DatastoreException {
		PersistenceManager pm = null;
		try {
			pm = PMF.get();
			Query query = pm.newQuery(getJdoClass());
			query.setRange(start, end);
			query.setFilter(attribute + "==pValue");
			query.declareParameters(value.getClass().getName() + " pValue");
			@SuppressWarnings("unchecked")
			List<T> list = ((List<T>) query.execute(value));
			List<S> ans = new ArrayList<S>();
			for (T jdo : list) {
				S dto = newDTO();
				copyToDto(jdo, dto);
				ans.add(dto);
			}
			return ans;
		} catch (Exception e) {
			throw new DatastoreException(e);
		} finally {
			pm.close();
		}
	}

}
