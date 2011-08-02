package org.sagebionetworks.repo.model.bootstrap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.repo.model.AccessControlList;
import org.sagebionetworks.repo.model.AccessControlListDAO;
import org.sagebionetworks.repo.model.AuthorizationConstants.DEFAULT_GROUPS;
import org.sagebionetworks.repo.model.DatastoreException;
import org.sagebionetworks.repo.model.Node;
import org.sagebionetworks.repo.model.NodeDAO;
import org.sagebionetworks.repo.model.NodeInheritanceDAO;
import org.sagebionetworks.repo.web.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "classpath:dao-beans.spb.xml" })
public class EntityBootstrapperAutowireTest {
	
	@Autowired
	NodeDAO nodeDao;
	@Autowired
	EntityBootstrapper entityBootstrapper;
	@Autowired
	private AccessControlListDAO accessControlListDAO;
	@Autowired
	NodeInheritanceDAO nodeInheritanceDao;
	
	@Test
	public void testBuildGroupMap() throws DatastoreException{
		Map<DEFAULT_GROUPS, String> map = entityBootstrapper.buildGroupMap();
		assertNotNull(map);
		assertEquals(DEFAULT_GROUPS.values().length, map.size());
		 DEFAULT_GROUPS[] array = DEFAULT_GROUPS.values();
		 for(DEFAULT_GROUPS group: array){
			 String id = map.get(group);
			 // There should be an id for each group.
			 assertNotNull(id);
		 }
	}
	
	@Test
	public void testBootstrap() throws DatastoreException, NotFoundException{
		assertNotNull(nodeDao);
		assertNotNull(entityBootstrapper);
		assertNotNull(entityBootstrapper.getBootstrapEntities());
		// Make sure we can find each entity
		List<EntityBootstrapData> list = entityBootstrapper.getBootstrapEntities();
		for(EntityBootstrapData entityBoot: list){
			// Look up the entity
			String id = nodeDao.getNodeIdForPath(entityBoot.getEntityPath());
			assertNotNull(id);
			Node node = nodeDao.getNode(id);
			assertNotNull(node);
			String benenefactorId = nodeInheritanceDao.getBenefactor(id);
			// This node should inherit from itself
			assertEquals("A bootstrapped node should be its own benefactor",id, benenefactorId);
			AccessControlList acl = accessControlListDAO.getForResource(id);
			assertNotNull(acl);
		}
	}
	
	@Test
	public void testRerunBootstrap() throws Exception{
		assertNotNull(nodeDao);
		assertNotNull(entityBootstrapper);
		assertNotNull(entityBootstrapper.getBootstrapEntities());
		// Make sure that we can rerun the bootstrapper
		entityBootstrapper.afterPropertiesSet();
		// Make sure we can find each entity
		List<EntityBootstrapData> list = entityBootstrapper.getBootstrapEntities();
		for(EntityBootstrapData entityBoot: list){
			// Look up the entity
			String id = nodeDao.getNodeIdForPath(entityBoot.getEntityPath());
			assertNotNull(id);
		}
	}

}