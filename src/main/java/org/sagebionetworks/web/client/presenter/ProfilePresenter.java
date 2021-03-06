package org.sagebionetworks.web.client.presenter;

import java.util.ArrayList;
import java.util.List;

import org.sagebionetworks.repo.model.EntityHeader;
import org.sagebionetworks.repo.model.ProjectHeader;
import org.sagebionetworks.repo.model.ProjectListType;
import org.sagebionetworks.repo.model.Team;
import org.sagebionetworks.repo.model.UserProfile;
import org.sagebionetworks.repo.model.quiz.PassingRecord;
import org.sagebionetworks.schema.adapter.AdapterFactory;
import org.sagebionetworks.schema.adapter.JSONObjectAdapterException;
import org.sagebionetworks.web.client.ChallengeClientAsync;
import org.sagebionetworks.web.client.DisplayConstants;
import org.sagebionetworks.web.client.DisplayUtils;
import org.sagebionetworks.web.client.GlobalApplicationState;
import org.sagebionetworks.web.client.SynapseClientAsync;
import org.sagebionetworks.web.client.place.Certificate;
import org.sagebionetworks.web.client.place.LoginPlace;
import org.sagebionetworks.web.client.place.Profile;
import org.sagebionetworks.web.client.place.Synapse;
import org.sagebionetworks.web.client.place.Synapse.ProfileArea;
import org.sagebionetworks.web.client.presenter.ProfileFormWidget.ProfileUpdatedCallback;
import org.sagebionetworks.web.client.security.AuthenticationController;
import org.sagebionetworks.web.client.utils.Callback;
import org.sagebionetworks.web.client.view.ProfileView;
import org.sagebionetworks.web.client.widget.entity.browse.EntityBrowserUtils;
import org.sagebionetworks.web.client.widget.team.TeamListWidget;
import org.sagebionetworks.web.shared.ChallengeBundle;
import org.sagebionetworks.web.shared.ChallengePagedResults;
import org.sagebionetworks.web.shared.MembershipInvitationBundle;
import org.sagebionetworks.web.shared.ProjectPagedResults;
import org.sagebionetworks.web.shared.exceptions.ConflictException;
import org.sagebionetworks.web.shared.exceptions.NotFoundException;

import com.google.gwt.activity.shared.AbstractActivity;
import com.google.gwt.event.shared.EventBus;
import com.google.gwt.place.shared.Place;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.AcceptsOneWidget;
import com.google.inject.Inject;

public class ProfilePresenter extends AbstractActivity implements ProfileView.Presenter, Presenter<Profile> {
		
	private Profile place;
	private ProfileView view;
	private SynapseClientAsync synapseClient;
	private ChallengeClientAsync challengeClient;
	private AuthenticationController authenticationController;
	private GlobalApplicationState globalApplicationState;
	
	private ProfileFormWidget profileForm;
	private AdapterFactory adapterFactory;
	private ProfileUpdatedCallback profileUpdatedCallback;
	private int teamNotificationCount;
	private String currentUserId;
	private boolean isOwner;
	private int currentProjectOffset, currentChallengeOffset;
	public final static int PROJECT_PAGE_SIZE=100;
	public final static int CHALLENGE_PAGE_SIZE=100;
	public ProjectFilterEnum filterType;
	public Team filterTeam;
	
	@Inject
	public ProfilePresenter(ProfileView view,
			AuthenticationController authenticationController,
			GlobalApplicationState globalApplicationState,
			SynapseClientAsync synapseClient,
			ProfileFormWidget profileForm,
			AdapterFactory adapterFactory,
			ChallengeClientAsync challengeClient
			) {
		this.view = view;
		this.authenticationController = authenticationController;
		this.globalApplicationState = globalApplicationState;
		this.synapseClient = synapseClient;
		this.adapterFactory = adapterFactory;
		this.profileForm = profileForm;
		this.challengeClient = challengeClient;
		view.setPresenter(this);
	}

	@Override
	public void start(AcceptsOneWidget panel, EventBus eventBus) {
		// Install the view
		panel.setWidget(view);
		
	}

	@Override
	public void setPlace(Profile place) {
		this.place = place;
		this.view.setPresenter(this);
		this.view.clear();
		showView(place);
	}
	
	@Override
    public String mayStop() {
        view.clear();
        return null;
    }
	
	public void editMyProfile() {
		if (checkIsLoggedIn())
			goTo(new Profile(authenticationController.getCurrentUserPrincipalId(), ProfileArea.SETTINGS));
	}
	
	public void viewMyProfile() {
		if (checkIsLoggedIn())
			goTo(new Profile(authenticationController.getCurrentUserPrincipalId()));
	}
	
	@Override
	public void goTo(Place place) {
		globalApplicationState.getPlaceChanger().goTo(place);
	}
	
	@Override
	public void updateArea(ProfileArea area) {
		if (area != null && !area.equals(place.getArea())) {
			place.setArea(area);
			place.setNoRestartActivity(true);
			globalApplicationState.getPlaceChanger().goTo(place);
		}
	}
	
	/**
	 * This method will update the current user's profile using LinkedIn
	 */
	@Override
	public void updateProfileWithLinkedIn(String requestToken, String verifier) {
		profileForm.updateProfileWithLinkedIn(requestToken, verifier);
	}
	
	private void updateProfileView(String userId, final ProfileArea initialTab) {
		view.clear();
		view.showLoading();
		isOwner = authenticationController.isLoggedIn() && authenticationController.getCurrentUserPrincipalId().equals(userId);
		currentUserId = userId == null ? authenticationController.getCurrentUserPrincipalId() : userId;
		synapseClient.getUserProfile(currentUserId, new AsyncCallback<UserProfile>() {
				@Override
				public void onSuccess(UserProfile profile) {
						if (isOwner) {
							//only configure the profile form (editor) if owner of this profile
							profileForm.configure(profile, profileUpdatedCallback);
						}
						
						getIsCertifiedAndUpdateView(profile, isOwner, initialTab);
					}
				@Override
				public void onFailure(Throwable caught) {
					view.hideLoading();
					DisplayUtils.handleServiceException(caught, globalApplicationState, authenticationController.isLoggedIn(), view);    					    				
				}
			});
	}
	
	public void getIsCertifiedAndUpdateView(final UserProfile profile, final boolean isOwner, final ProfileArea area) {
		synapseClient.getCertifiedUserPassingRecord(profile.getOwnerId(), new AsyncCallback<String>() {
			@Override
			public void onSuccess(String passingRecordJson) {
				try {
					view.hideLoading();
					PassingRecord passingRecord = new PassingRecord(adapterFactory.createNew(passingRecordJson));
					view.updateView(profile, isOwner, passingRecord, profileForm.asWidget());
					tabClicked(area);
					proceed();
				} catch (JSONObjectAdapterException e) {
					onFailure(e);
				}
			}
			@Override
			public void onFailure(Throwable caught) {
				view.hideLoading();
				if (caught instanceof NotFoundException) {
					view.updateView(profile, isOwner, null, profileForm.asWidget());
					tabClicked(area);
				}
				else
					view.showErrorMessage(caught.getMessage());
				
				proceed();
			}
			
			private void proceed() {
				setProjectFilterAndRefresh(ProjectFilterEnum.ALL, null);
				refreshTeams();
				refreshChallenges();
			}
		});
	}
	
	public void refreshProjects() {
		currentProjectOffset = 0;
		view.clearProjects();
		getMoreProjects();
	}
	
	public void refreshChallenges() {
		currentChallengeOffset = 0;
		view.clearChallenges();
		getMoreChallenges();
	}
	
	/**
	 * Sets the project filter.  If filtered to a specific team, then the Team argument will be used.
	 * @param filterType
	 * @param team
	 */
	public void setProjectFilterAndRefresh(ProjectFilterEnum filterType, Team team) {
		this.filterType =filterType;
		filterTeam = team;
		refreshProjects();
	}

	public void getMoreProjects() {
		if (isOwner) {
			view.showProjectFiltersUI();
			//this depends on the active filter
			switch (filterType) {
				case ALL:
					view.setAllProjectsFilterSelected();
					getMyProjects(ProjectListType.MY_PROJECTS, ProjectFilterEnum.ALL, currentProjectOffset);
					break;
				case MINE:
					view.setMyProjectsFilterSelected();
					getMyProjects(ProjectListType.MY_CREATED_PROJECTS, ProjectFilterEnum.MINE, currentProjectOffset);
					break;
				case MY_PARTICIPATED_PROJECTS:
					view.setTeamsFilterSelected();
					getMyProjects(ProjectListType.MY_PARTICIPATED_PROJECTS, ProjectFilterEnum.MY_PARTICIPATED_PROJECTS, currentProjectOffset);
					break;
				case MY_TEAM_PROJECTS:
					view.setTeamsFilterSelected();
					getMyProjects(ProjectListType.MY_TEAM_PROJECTS, ProjectFilterEnum.MY_TEAM_PROJECTS, currentProjectOffset);
					break;
				case FAVORITES:
					view.setFavoritesFilterSelected();
					getFavorites();
					break;
				case TEAM:
					view.setTeamsFilterSelected();
					getTeamProjects(currentProjectOffset);
					break;
				default:
					break;
			}
		} else
			getUserProjects(currentProjectOffset);
	}
	
	@Override
	public void refreshTeams() {
		teamNotificationCount = 0;
		view.clearTeamNotificationCount();
		if (isOwner)
			view.refreshTeamInvites();
		getTeams(currentUserId);
	}
	
	public void getTeams(String userId) {
		AsyncCallback<List<Team>> teamCallback = new AsyncCallback<List<Team>>() {
			@Override
			public void onFailure(Throwable caught) {
				view.setTeamsError(caught.getMessage());
			}
			@Override
			public void onSuccess(List<Team> teams) {
				view.setTeams(teams,isOwner);
				view.setTeamsFilterVisible(!teams.isEmpty());
			}
		};
		TeamListWidget.getTeams(userId, synapseClient, adapterFactory, teamCallback);
	}
	
	
	public void getMoreChallenges() {
		view.showChallengesLoading(true);
		challengeClient.getChallenges(currentUserId, CHALLENGE_PAGE_SIZE, currentChallengeOffset, new AsyncCallback<ChallengePagedResults>() {
			@Override
			public void onSuccess(ChallengePagedResults challengeResults) {
				addChallengeResults(challengeResults.getResults());
				challengePageAdded(challengeResults.getTotalNumberOfResults());
			}
	            @Override
			public void onFailure(Throwable caught) {
				view.showChallengesLoading(false);
				view.setChallengesError("Could not load challenges:" + caught.getMessage());
			}
		});
	}
	
	public void getMyProjects(ProjectListType projectListType, final ProjectFilterEnum filter, int offset) {
		view.showProjectsLoading(true);
		synapseClient.getMyProjects(projectListType, PROJECT_PAGE_SIZE, offset, new AsyncCallback<ProjectPagedResults>() {
			@Override
			public void onSuccess(ProjectPagedResults projectHeaders) {
				if (filterType == filter) {
					addProjectResults(projectHeaders.getResults());
					projectPageAdded(projectHeaders.getTotalNumberOfResults());
				}
			}
			@Override
			public void onFailure(Throwable caught) {
				view.showProjectsLoading(false);
				view.setProjectsError("Could not load my projects:" + caught.getMessage());
			}
		});
	}
	
	public void getTeamProjects(int offset) {
		view.showProjectsLoading(true);
		synapseClient.getProjectsForTeam(filterTeam.getId(), PROJECT_PAGE_SIZE, offset, new AsyncCallback<ProjectPagedResults>() {
			@Override
			public void onSuccess(ProjectPagedResults projectHeaders) {
				if (filterType == ProjectFilterEnum.TEAM) {
					addProjectResults(projectHeaders.getResults());
					projectPageAdded(projectHeaders.getTotalNumberOfResults());
				}
			}
			@Override
			public void onFailure(Throwable caught) {
				view.showProjectsLoading(false);
				view.setProjectsError("Could not load team projects:" + caught.getMessage());
			}
		});
	}

	public void getUserProjects(int offset) {
		view.showProjectsLoading(true);
		synapseClient.getUserProjects(currentUserId, PROJECT_PAGE_SIZE, offset, new AsyncCallback<ProjectPagedResults>() {
			@Override
			public void onSuccess(ProjectPagedResults projectHeaders) {
				List<ProjectHeader> headers = projectHeaders.getResults();
				addProjectResults(headers);
				projectPageAdded(projectHeaders.getTotalNumberOfResults());
			}
			@Override
			public void onFailure(Throwable caught) {
				view.showProjectsLoading(false);
				view.setProjectsError("Could not load user projects:" + caught.getMessage());
			}
		});
	}
	
	public void addProjectResults(List<ProjectHeader> headers) {
		view.showProjectsLoading(false);
		view.addProjects(headers);
	}
	
	public void addChallengeResults(List<ChallengeBundle> challenges) {
		view.showChallengesLoading(false);
		view.addChallenges(challenges);
	}
	
	public void projectPageAdded(int totalNumberOfResults) {
		currentProjectOffset += PROJECT_PAGE_SIZE;
		view.setIsMoreProjectsVisible(currentProjectOffset < totalNumberOfResults);
	}
	
	public void challengePageAdded(Long totalNumberOfResults) {
		currentChallengeOffset += CHALLENGE_PAGE_SIZE;
		view.setIsMoreChallengesVisible(currentChallengeOffset < totalNumberOfResults);
	}

	
	public void getFavorites() {
		view.showProjectsLoading(true);
		EntityBrowserUtils.loadFavorites(synapseClient, adapterFactory, globalApplicationState, new AsyncCallback<List<EntityHeader>>() {
			@Override
			public void onSuccess(List<EntityHeader> result) {
				if (filterType == ProjectFilterEnum.FAVORITES) {
					//convert to Project Headers
					if (result.size() == 0) {
						view.showProjectsLoading(false);
						view.setFavoritesHelpPanelVisible(true);
					} else {
						List<ProjectHeader> headers = new ArrayList<ProjectHeader>(result.size());
						for (EntityHeader header : result) {
							ProjectHeader projectHeader = new ProjectHeader();
							projectHeader.setId(header.getId());
							projectHeader.setName(header.getName());
							headers.add(projectHeader);
						}
						addProjectResults(headers);
						view.setIsMoreProjectsVisible(false);	
					}
				}
			}
			@Override
			public void onFailure(Throwable caught) {
				view.showProjectsLoading(false);
				view.setProjectsError("Could not load user favorites:" + caught.getMessage());
			}
		});
	}
	
	
	@Override
	public void createProject(final String name) {
		//validate project name
		if (!DisplayUtils.isDefined(name)) {
			view.showErrorMessage(DisplayConstants.PLEASE_ENTER_PROJECT_NAME);
			return;
		}
		
		CreateEntityUtil.createProject(name, synapseClient, adapterFactory, globalApplicationState, authenticationController, new AsyncCallback<String>() {
			@Override
			public void onSuccess(String newProjectId) {
				view.showInfo(DisplayConstants.LABEL_PROJECT_CREATED, name);
				globalApplicationState.getPlaceChanger().goTo(new Synapse(newProjectId));						
			}
			
			@Override
			public void onFailure(Throwable caught) {
				if(caught instanceof ConflictException) {
					view.showErrorMessage(DisplayConstants.WARNING_PROJECT_NAME_EXISTS);
				} else {
					if(!DisplayUtils.handleServiceException(caught, globalApplicationState, authenticationController.isLoggedIn(), view)) {					
						view.showErrorMessage(DisplayConstants.ERROR_GENERIC_RELOAD);
					} 
				}
			}
		});
	}
	

	@Override
	public void createTeam(final String teamName) {
		//validate team name
		if (!DisplayUtils.isDefined(teamName)) {
			view.showErrorMessage(DisplayConstants.PLEASE_ENTER_TEAM_NAME);
			return;
		}

		synapseClient.createTeam(teamName, new AsyncCallback<String>() {
			@Override
			public void onSuccess(String newTeamId) {
				view.showInfo(DisplayConstants.LABEL_TEAM_CREATED, teamName);
				globalApplicationState.getPlaceChanger().goTo(new org.sagebionetworks.web.client.place.Team(newTeamId));						
			}
			
			@Override
			public void onFailure(Throwable caught) {
				if(caught instanceof ConflictException) {
					view.showErrorMessage(DisplayConstants.WARNING_TEAM_NAME_EXISTS);
				} else {
					if(!DisplayUtils.handleServiceException(caught, globalApplicationState, authenticationController.isLoggedIn(), view)) {					
						view.showErrorMessage(caught.getMessage());
					}
				}
			}
		});
	}
	
	private boolean checkIsLoggedIn() {
		if (!authenticationController.isLoggedIn()) {
			view.showErrorMessage(DisplayConstants.ERROR_LOGIN_REQUIRED);
			globalApplicationState.getPlaceChanger().goTo(new LoginPlace(LoginPlace.LOGIN_TOKEN));
			return false;
		}
		return true;
	}
	
	private void setupProfileFormCallback() {
		profileUpdatedCallback = new ProfileUpdatedCallback() {
			@Override
			public void profileUpdateSuccess() {
				view.showInfo("Success", "Your profile has been updated.");
				continueToEditProfile();
			}
			
			public void continueToEditProfile() {
				editMyProfile();
				view.refreshHeader();
			}
			
			@Override
			public void onFailure(Throwable caught) {
				if (!DisplayUtils.handleServiceException(caught, globalApplicationState, authenticationController.isLoggedIn(), view)) {
					view.showErrorMessage(caught.getMessage());
				}
				continueToEditProfile();
			}
		};
	}
	
	private void loggedInCheck() {
		if (!authenticationController.isLoggedIn()) {
			view.showErrorMessage(DisplayConstants.ERROR_LOGIN_REQUIRED);
			globalApplicationState.getPlaceChanger().goTo(new LoginPlace(LoginPlace.LOGIN_TOKEN));
		}
	}
	
	private void showView(Profile place) {
		view.clear();
		setupProfileFormCallback();
		String token = place.toToken();
		if (authenticationController.isLoggedIn() && authenticationController.getCurrentUserPrincipalId().equals(place.getUserId())) {
			//View my profile
			updateProfileView(place.getUserId(), place.getArea());
		}
		else if(!"".equals(token) && token != null) {
			//if this contains an oauth_token, it's from linkedin
			if (token.contains("oauth_token"))
			{
				// User just logged in to LinkedIn. Get the request token and their info to update
				// their profile with.

				//must be logged in
				loggedInCheck();

				String requestToken = "";
				String verifier = "";
				if (token.startsWith("?"))
					token = token.substring(1);
				String[] oAuthTokens = token.split("&");
				for(String s : oAuthTokens) {
					String[] tokenParts = s.split("=");
					if(tokenParts[0].equals("oauth_token")) {
						requestToken = tokenParts[1];
					} else if(tokenParts[0].equals("oauth_verifier")) {
						verifier = tokenParts[1];
					}
				}
				
				if(!requestToken.equals("") && !verifier.equals("")) {
					updateProfileWithLinkedIn(requestToken, verifier);
				} else {
					view.showErrorMessage("An error occurred. Please try reloading the page.");
				}
			} else if (Profile.EDIT_PROFILE_TOKEN.equals(token)) {
				editMyProfile();
			} else {
				//otherwise, this is a user id
				updateProfileView(place.getUserId(), place.getArea());
			}
		}
	}
	
	@Override
	public void updateTeamInvites(List<MembershipInvitationBundle> invites) {
		if (invites != null && invites.size() > 0) {
			teamNotificationCount += invites.size();
			//update team notification count
			if (teamNotificationCount > 0)
				view.setTeamNotificationCount(Integer.toString(teamNotificationCount));
}
	}

	@Override
	public void addMembershipRequests(int count) {
		teamNotificationCount += count;
		if (teamNotificationCount > 0)
			view.setTeamNotificationCount(Integer.toString(teamNotificationCount));
	}
	
	/**
	 * Exposed for test purposes only
	 */
	public int getTeamNotificationCount() {
		return teamNotificationCount;
	}
	
	public void setTeamNotificationCount(int teamNotificationCount) {
		this.teamNotificationCount = teamNotificationCount;
	}
	
	/**
	 * Exposed for unit testing purposes only
	 * @return
	 */
	public int getCurrentOffset() {
		return currentProjectOffset;
	}

	/**
	 * Exposed for unit testing purposes only
	 * @return
	 */
	public void setCurrentOffset(int currentOffset) {
		this.currentProjectOffset = currentOffset;
	}
	
	/**
	 * Exposed for unit testing purposes only
	 * @return
	 */
	public int getCurrentChallengeOffset() {
		return currentChallengeOffset;
	}
	
	/**
	 * Exposed for unit testing purposes only
	 * @return
	 */
	public boolean isOwner() {
		return isOwner;
	}
	
	@Override
	public void tabClicked(final ProfileArea tab) {
		if (tab == null) {
			view.showErrorMessage("The selected tab is undefined.");
			return;
		}
		//if we are editing, then pop up a confirm
		if (globalApplicationState.isEditing()) {
			Callback yesCallback = new Callback() {
				@Override
				public void invoke() {
					profileForm.rollback();
					view.setTabSelected(tab);
				}
			};
			view.showConfirmDialog("", DisplayConstants.NAVIGATE_AWAY_CONFIRMATION_MESSAGE, yesCallback);
		} else
			view.setTabSelected(tab);
	}
	
	/**
	 * Exposed for unit testing purposes only
	 * @return
	 */
	public void setIsOwner(boolean isOwner) {
		this.isOwner = isOwner;
	}
	
	@Override
	public void certificationBadgeClicked() {
		goTo(new Certificate(currentUserId));
	}
	
	@Override
	public void applyFilterClicked(ProjectFilterEnum filterType, Team team) {
		setProjectFilterAndRefresh(filterType, team);
	}
	
	/**
	 * For testing purposes only
	 * @param currentUserId
	 */
	public void setCurrentUserId(String currentUserId) {
		this.currentUserId = currentUserId;
	}
	
	/**
	 * For testing purposes only
	 */
	public ProjectFilterEnum getFilterType() {
		return filterType;
	}
}

