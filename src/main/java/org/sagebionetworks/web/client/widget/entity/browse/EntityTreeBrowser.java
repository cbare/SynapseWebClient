package org.sagebionetworks.web.client.widget.entity.browse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.sagebionetworks.repo.model.EntityHeader;
import org.sagebionetworks.schema.adapter.AdapterFactory;
import org.sagebionetworks.schema.adapter.JSONObjectAdapterException;
import org.sagebionetworks.web.client.DisplayConstants;
import org.sagebionetworks.web.client.DisplayUtils;
import org.sagebionetworks.web.client.EntityTypeProvider;
import org.sagebionetworks.web.client.GlobalApplicationState;
import org.sagebionetworks.web.client.IconsImageBundle;
import org.sagebionetworks.web.client.SearchServiceAsync;
import org.sagebionetworks.web.client.events.EntitySelectedEvent;
import org.sagebionetworks.web.client.events.EntitySelectedHandler;
import org.sagebionetworks.web.client.events.EntityUpdatedEvent;
import org.sagebionetworks.web.client.events.EntityUpdatedHandler;
import org.sagebionetworks.web.client.security.AuthenticationController;
import org.sagebionetworks.web.client.widget.SynapseWidgetPresenter;
import org.sagebionetworks.web.client.widget.entity.EntityTreeItem;
import org.sagebionetworks.web.shared.EntityType;
import org.sagebionetworks.web.shared.QueryConstants.WhereOperator;
import org.sagebionetworks.web.shared.WebConstants;
import org.sagebionetworks.web.shared.WhereCondition;
import org.sagebionetworks.web.shared.exceptions.UnknownErrorException;

import com.google.gwt.event.shared.HandlerManager;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

public class EntityTreeBrowser implements EntityTreeBrowserView.Presenter, SynapseWidgetPresenter {
	
	private EntityTreeBrowserView view;
	private SearchServiceAsync searchService;
	private AuthenticationController authenticationController;
	private GlobalApplicationState globalApplicationState;
	private HandlerManager handlerManager = new HandlerManager(this);
	private IconsImageBundle iconsImageBundle;
	AdapterFactory adapterFactory;
	EntityTypeProvider entityTypeProvider;
	private Set<EntityTreeItem> alreadyFetchedEntityChildren;
	
	private String currentSelection;
	
	private final int MAX_FOLDER_LIMIT = 500;
	private String currentFolderChildrenEntityId;
	
	@Inject
	public EntityTreeBrowser(EntityTreeBrowserView view,
			SearchServiceAsync searchService,
			AuthenticationController authenticationController,
			EntityTypeProvider entityTypeProvider,
			GlobalApplicationState globalApplicationState,
			IconsImageBundle iconsImageBundle,
			AdapterFactory adapterFactory) {
		this.view = view;		
		this.searchService = searchService;
		this.entityTypeProvider = entityTypeProvider;
		this.authenticationController = authenticationController;
		this.globalApplicationState = globalApplicationState;
		this.iconsImageBundle = iconsImageBundle;
		this.adapterFactory = adapterFactory;
		alreadyFetchedEntityChildren = new HashSet<EntityTreeItem>();
		
		view.setPresenter(this);
	}	
	
	public void clearState() {
		view.clear();
		// remove handlers
		handlerManager = new HandlerManager(this);		
	}
	
	public void clear() {
		view.clear();
	}

	/**
	 * Configure tree view with given entityId's children as start set
	 * @param entityId
	 */
	public void configure(String entityId, final boolean sort) {
		view.clear();
		view.showLoading();
		getFolderChildren(entityId, new AsyncCallback<List<EntityHeader>>() {
			@Override
			public void onSuccess(List<EntityHeader> result) {
				if (sort)
					EntityBrowserUtils.sortEntityHeadersByName(result);
				view.setRootEntities(result);
			}
			@Override
			public void onFailure(Throwable caught) {
				DisplayUtils.handleServiceException(caught, globalApplicationState, authenticationController.isLoggedIn(), view);
			}
		});
	}
	
	/**
	 * Configure tree view to be filled initially with the given headers.
	 * @param rootEntities
	 */
	public void configure(List<EntityHeader> rootEntities, boolean sort) {
		if (sort)
			EntityBrowserUtils.sortEntityHeadersByName(rootEntities);
		view.setRootEntities(rootEntities);
	}
	
	@Override
	public Widget asWidget() {
		view.setPresenter(this);		
		return view.asWidget();
	}
	
	@Override
	public void getFolderChildren(final String entityId, final AsyncCallback<List<EntityHeader>> asyncCallback) {
		this.currentFolderChildrenEntityId = entityId;
		// NOTE: this is fragile, but there doesn't seem to be a way around querying by nodeType. 
		// a query on concreteType!=org...TableEntity eliminates nodes who do not have concreteType defined
		final String TABLE_ENTITY_NODE_TYPE_ID = "17"; 
		
		searchService.searchEntities("entity", Arrays
				.asList(new WhereCondition[] { 
						new WhereCondition("parentId", WhereOperator.EQUALS, entityId),
						new WhereCondition(WebConstants.NODE_TYPE_KEY, WhereOperator.NOT_EQUALS, TABLE_ENTITY_NODE_TYPE_ID)
						}), 1, MAX_FOLDER_LIMIT, null,
				false, new AsyncCallback<List<String>>() {
				@Override
				public void onSuccess(List<String> result) {
					//only process results if these are for the currently requested entity id
					if (currentFolderChildrenEntityId.equals(entityId)) {
						List<EntityHeader> headers = new ArrayList<EntityHeader>();
						for(String entityHeaderJson : result) {
							try {
								headers.add(new EntityHeader(adapterFactory.createNew(entityHeaderJson)));
							} catch (JSONObjectAdapterException e) {
								onFailure(new UnknownErrorException(DisplayConstants.ERROR_INCOMPATIBLE_CLIENT_VERSION));
							}
						}
						asyncCallback.onSuccess(headers);
					}
				}
				@Override
				public void onFailure(Throwable caught) {
					DisplayUtils.handleServiceException(caught, globalApplicationState, authenticationController.isLoggedIn(), view);				
					asyncCallback.onFailure(caught);
				}
			});					
	}

	@Override
	public void setSelection(String id) {
		currentSelection = id;
		fireEntitySelectedEvent();
	}	
	
	public String getSelected() {
		return currentSelection;
	}
		
	@SuppressWarnings("unchecked")
	public void addEntitySelectedHandler(EntitySelectedHandler handler) {
		handlerManager.addHandler(EntitySelectedEvent.getType(), handler);		
	}

	@SuppressWarnings("unchecked")
	public void removeEntitySelectedHandler(EntitySelectedHandler handler) {
		handlerManager.removeHandler(EntitySelectedEvent.getType(), handler);
	}
	
	@SuppressWarnings("unchecked")
	public void addEntityUpdatedHandler(EntityUpdatedHandler handler) {
		handlerManager.addHandler(EntityUpdatedEvent.getType(), handler);		
	}

	@SuppressWarnings("unchecked")
	public void removeEntityUpdatedHandler(EntityUpdatedHandler handler) {
		handlerManager.removeHandler(EntityUpdatedEvent.getType(), handler);
	}
	
	@Override
	public int getMaxLimit() {
		return MAX_FOLDER_LIMIT;
	}

	/**
	 * Rather than linking to the Entity Page, a clicked entity
	 * in the tree will become selected.
	 */
	public void makeSelectable() {
		view.makeSelectable();
	}
	
	/**
	 * When a node is expanded, if its children have not already
	 * been fetched and placed into the tree, it will delete the dummy
	 * child node and fetch the actual children of the expanded node.
	 * During this process, the icon of the expanded node is switched
	 * to a loading indicator.
	 */
	@Override
	public void expandTreeItemOnOpen(final EntityTreeItem target) {
		if (!alreadyFetchedEntityChildren.contains(target)) {
			// We have not already fetched children for this entity.
			
			// Change to loading icon.
			target.showLoadingIcon();
			
			getFolderChildren(target.getHeader().getId(), new AsyncCallback<List<EntityHeader>>() {
				
				@Override
				public void onSuccess(List<EntityHeader> result) {
					// We got the children.
					alreadyFetchedEntityChildren.add(target);
					target.asTreeItem().removeItems();	// Remove the dummy item.
					
					// Make a tree item for each child and place them in the tree.
					for (EntityHeader header : result) {
						view.createAndPlaceTreeItem(header, target, false);
					}
					
					// Change back to type icon.
					target.showTypeIcon();
				}
				
				@Override
				public void onFailure(Throwable caught) {
					if (!DisplayUtils.handleServiceException(caught, globalApplicationState, authenticationController.isLoggedIn(), view)) {                    
						view.showErrorMessage(caught.getMessage());
					}
				}
				
			});
		}
	}
	
	@Override
	public void clearRecordsFetchedChildren() {
		alreadyFetchedEntityChildren.clear();
	}
	
	/*
	 * Private Methods
	 */
	private void fireEntitySelectedEvent() {
		handlerManager.fireEvent(new EntitySelectedEvent());
	}

	@Override
	public ImageResource getIconForType(String type) {
		return getIconForType(type, entityTypeProvider, iconsImageBundle);
	}
	
	public static ImageResource getIconForType(String type, EntityTypeProvider entityTypeProvider, IconsImageBundle iconsImageBundle) {
		if(type == null) return null;
		EntityType entityType;
		if(type.startsWith("org.")) entityType = entityTypeProvider.getEntityTypeForClassName(type); 			
		else entityType = entityTypeProvider.getEntityTypeForString(type);
		if (entityType == null) return null;
		return DisplayUtils.getSynapseIconForEntityClassName(entityType.getClassName(), DisplayUtils.IconSize.PX16, iconsImageBundle);
	}

	/**
	 * For testing purposes only
	 * @param currentFolderChildrenEntityId
	 */
	public void setCurrentFolderChildrenEntityId(String currentFolderChildrenEntityId) {
		this.currentFolderChildrenEntityId = currentFolderChildrenEntityId;
	}
}
