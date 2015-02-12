package org.sagebionetworks.web.client.widget.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.sagebionetworks.evaluation.model.Evaluation;
import org.sagebionetworks.evaluation.model.Submission;
import org.sagebionetworks.repo.model.Challenge;
import org.sagebionetworks.repo.model.Entity;
import org.sagebionetworks.repo.model.Reference;
import org.sagebionetworks.repo.model.Team;
import org.sagebionetworks.repo.model.Versionable;
import org.sagebionetworks.schema.adapter.JSONObjectAdapterException;
import org.sagebionetworks.web.client.ChallengeClientAsync;
import org.sagebionetworks.web.client.DisplayConstants;
import org.sagebionetworks.web.client.DisplayUtils;
import org.sagebionetworks.web.client.GlobalApplicationState;
import org.sagebionetworks.web.client.SynapseClientAsync;
import org.sagebionetworks.web.client.place.LoginPlace;
import org.sagebionetworks.web.client.place.Profile;
import org.sagebionetworks.web.client.security.AuthenticationController;
import org.sagebionetworks.web.client.transform.NodeModelCreator;
import org.sagebionetworks.web.client.widget.entity.EvaluationSubmitterView.Presenter;
import org.sagebionetworks.web.shared.EntityWrapper;
import org.sagebionetworks.web.shared.PaginatedResults;
import org.sagebionetworks.web.shared.exceptions.RestServiceException;
import org.sagebionetworks.web.shared.exceptions.UnknownErrorException;

import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

public class EvaluationSubmitter implements Presenter {

	private EvaluationSubmitterView view;
	private SynapseClientAsync synapseClient;
	private ChallengeClientAsync challengeClient;
	private NodeModelCreator nodeModelCreator;
	private GlobalApplicationState globalApplicationState;
	private AuthenticationController authenticationController;
	private Entity submissionEntity;
	private String submissionEntityId, submissionName;
	private Long submissionEntityVersion;
	List<Team> teams;
	private Evaluation evaluation;
	private Challenge challenge;
	private Team selectedTeam;
	private String selectedTeamMemberStateHash;
	private List<Long> selectedTeamEligibleMembers;
	@Inject
	public EvaluationSubmitter(EvaluationSubmitterView view,
			SynapseClientAsync synapseClient,
			NodeModelCreator nodeModelCreator,
			GlobalApplicationState globalApplicationState,
			AuthenticationController authenticationController,
			ChallengeClientAsync challengeClient) {
		this.view = view;
		this.view.setPresenter(this);
		this.synapseClient = synapseClient;
		this.nodeModelCreator = nodeModelCreator;
		this.globalApplicationState = globalApplicationState;
		this.authenticationController = authenticationController;
		this.challengeClient = challengeClient;
	}
	
	/**
	 * 
	 * @param submissionEntity set to null if an entity finder should be shown
	 * @param evaluationIds set to null if we should query for all available evaluations
	 */
	public void configure(Entity submissionEntity, Set<String> evaluationIds) {
		challenge = null;
		evaluation = null;
		selectedTeam = null;
		teams = new ArrayList<Team>();
		selectedTeamEligibleMembers = new ArrayList<Long>();
		view.showLoading();
		this.submissionEntity = submissionEntity;
		try {
			if (evaluationIds == null)
				challengeClient.getAvailableEvaluations(getEvalCallback());
			else
				challengeClient.getAvailableEvaluations(evaluationIds, getEvalCallback());
		} catch (RestServiceException e) {
			view.showErrorMessage(e.getMessage());
		}
	}
	
	private AsyncCallback<String> getEvalCallback() {
		return new AsyncCallback<String>() {
			@Override
			public void onSuccess(String jsonString) {
				try {
					PaginatedResults<Evaluation> results = nodeModelCreator.createPaginatedResults(jsonString, Evaluation.class);
					List<Evaluation> evaluations = results.getResults();
					if (evaluations == null || evaluations.size() == 0) {
						//no available evaluations, pop up an info dialog
						view.showErrorMessage(DisplayConstants.NOT_PARTICIPATING_IN_ANY_EVALUATIONS);
					} 
					else {
						view.showModal1(submissionEntity == null, evaluations);
					}
				} catch (JSONObjectAdapterException e) {
					onFailure(new UnknownErrorException(DisplayConstants.ERROR_INCOMPATIBLE_CLIENT_VERSION));
				}
			}
			
			@Override
			public void onFailure(Throwable caught) {
				if(!DisplayUtils.handleServiceException(caught, globalApplicationState, authenticationController.isLoggedIn(), view))
					view.showErrorMessage(caught.getMessage());
			}
		};
	}
	
		
	@Override
	public void nextClicked(Reference selectedReference, String submissionName, Evaluation evaluation) {
		//in any case look up the entity (to make sure we have the most recent version, for the current etag
		submissionEntityVersion = null;
		if (submissionEntity != null) {
			submissionEntityId = submissionEntity.getId();
			if (submissionEntity instanceof Versionable)
				submissionEntityVersion = ((Versionable)submissionEntity).getVersionNumber();
		}
		else {
			submissionEntityId = selectedReference.getTargetId();
			submissionEntityVersion = selectedReference.getTargetVersionNumber();
		}
		this.submissionName = submissionName;
		this.evaluation = evaluation;
		//The standard is to attach access requirements to the associated team, and show them when joining the team.
		//So access requirements are not checked again here.
		view.hideModal1();
		if (evaluation.getContentSource() == null) {
			//no need to show second page, this is a submission to a non-challenge eval queue.
			doneClicked();
		} else {
			queryForChallenge();
		}
	}
	
	public void queryForChallenge() {
		challengeClient.getChallenge(evaluation.getContentSource(), new AsyncCallback<Challenge>() {
			@Override
			public void onSuccess(Challenge result) {
				challenge = result;
				getAvailableTeams();
			}
			@Override
			public void onFailure(Throwable caught) {
				view.showErrorMessage("Unable to find associated challenge: " + caught.getMessage());
			}
		});
	}
	
	public void getAvailableTeams() {
		challengeClient.getSubmissionTeams(authenticationController.getCurrentUserPrincipalId(), challenge.getId(), getTeamsCallback());
	}
	
	@Override
	public void teamAdded() {
		//when a team is added, we need to refresh the teams list
		getAvailableTeams();
	}
	
	@Override
	public void createNewTeamClicked() {
		if (authenticationController.isLoggedIn())
			globalApplicationState.getPlaceChanger().goTo(new Profile(authenticationController.getCurrentUserPrincipalId()));
		else {
			globalApplicationState.getPlaceChanger().goTo(new LoginPlace(LoginPlace.LOGIN_TOKEN));
		}
	}
	
	@Override
	public void registerMyTeamLinkClicked() {
		view.showRegisterTeamDialog(challenge.getId());
	}
	
	private AsyncCallback<List<Team>> getTeamsCallback() {
		return new AsyncCallback<List<Team>>() {
			@Override
			public void onSuccess(List<Team> results) {
				teams = results;
				view.setTeams(teams);
				view.showModal2();
			}
			
			@Override
			public void onFailure(Throwable caught) {
				if(!DisplayUtils.handleServiceException(caught, globalApplicationState, authenticationController.isLoggedIn(), view))
					view.showErrorMessage(caught.getMessage());
			}
		};
	}
	
	@Override
	public void doneClicked() {
		if (!view.isIndividual()) {
			//team submission
			if (selectedTeam == null) {
				view.showErrorMessage("Please select a team");
				return;
			}
		}
		lookupEtagAndCreateSubmission(submissionEntityId, submissionEntityVersion);
	}
	@Override
	public void teamSelected(String selectedTeamName) {
		selectedTeam = null;
		selectedTeamMemberStateHash = null;
		selectedTeamEligibleMembers.clear();
		view.clearContributors();
		view.setTeamInEligibleErrorVisible(false, "");
		//resolve team from team name
		for (Team team : teams) {
			if(selectedTeamName.equals(team.getName())) {
				selectedTeam = team;
				break;
			}
		}
		if (selectedTeam != null) {
			//TODO: get contributor list for this team
//			challengeClient.getTeamSubmissionEligibility(evaluation.getId(), selectedTeam.getId(), new AsyncCallback<TeamSubmissionEligibility>() {
//				@Override
//				public void onSuccess(TeamSubmissionEligibility teamState) {
//					//is the team eligible???
//					if (!teamState.isEligible()) {
//						//show the error
//						String reason = ""; //unknown reason
//						if (!teamState.isRegistered()) {
//							reason = selectedTeam.getName() + " is not registered for this challenge. Please register this team, or select a different team.";
//						} else if (teamState.isQuotaFilled) {
//							reason = selectedTeam.getName() + " has exceeded the submission quota.";
//						}
//						view.setTeamInEligibleErrorVisible(true, reason);
//					} else {
//						selectedTeamMemberStateHash = teamState.getMemberStateHash();
//						for (MemberSubmissionEligibility memberState : teamState.getTeamMemberEligibilityList()) {
//							if (memberState.isEligible()) {
//								selectedTeamEligibleMembers.add(memberState.getPrincipalId());
//								view.addEligibleContributor(memberState.getPrincipalId());
//							} else {
//								String reason = ""; //unknown reason
//								if (!memberState.isRegistered()) {
//									reason = "Not registered for the challenge.";
//								} else if (memberState.isQuotaFilled) {
//									reason = "Exceeded the submission quota.";
//								}
//								view.addInEligibleContributor(memberState.getPrincipalId(), reason);
//							}
//						}
//					}
//				};
//				@Override
//				public void onFailure(Throwable caught) {
//					if(!DisplayUtils.handleServiceException(caught, globalApplicationState, authenticationController.isLoggedIn(), view))
//						view.showErrorMessage(caught.getMessage());
//				}
//			});
		}
	}
	
	public void lookupEtagAndCreateSubmission(final String id, final Long ver) {
		//look up entity for the current etag
		synapseClient.getEntity(id, new AsyncCallback<EntityWrapper>() {
			public void onSuccess(EntityWrapper result) {
				Entity entity;
				try {
					entity = nodeModelCreator.createEntity(result);
					Long v = null;
					if (ver != null)
						v = ver;
					else if (entity instanceof Versionable)
						v = ((Versionable)entity).getVersionNumber();
					 else {
						 //entity is not versionable, the service will not accept null, but will accept a version of 1
						v = 1L;
					 }
						 
					submitToEvaluation(id, v, entity.getEtag());
				} catch (JSONObjectAdapterException e) {
					onFailure(new UnknownErrorException(DisplayConstants.ERROR_INCOMPATIBLE_CLIENT_VERSION));
				}
			}
			
			@Override
			public void onFailure(Throwable caught) {
				if(!DisplayUtils.handleServiceException(caught, globalApplicationState, authenticationController.isLoggedIn(), view))
					view.showErrorMessage(caught.getMessage());
			}
		});
	}
	
	public void submitToEvaluation(final String entityId, final Long versionNumber, final String etag) {
		//set up shared values across all submissions
		Submission newSubmission = new Submission();
		newSubmission.setEntityId(entityId);
		newSubmission.setUserId(authenticationController.getCurrentUserPrincipalId());
		newSubmission.setVersionNumber(versionNumber);
		if (submissionName != null && submissionName.trim().length() > 0)
			newSubmission.setName(submissionName);
		if (!selectedTeamEligibleMembers.isEmpty()) {
			//TODO: set contributors
//			newSubmission.setContributors(selectedTeamEligibleMembers);
		}
		submitToEvaluation(newSubmission, etag);
	}
	
	public void submitToEvaluation(final Submission newSubmission, final String etag) {
		//and create a new submission for each evaluation
		newSubmission.setEvaluationId(evaluation.getId());
		try {
			String teamId = null;
			String memberStateHash = null;
			if (!view.isIndividual()) {
				//team is selected
				teamId = selectedTeam.getId();
				memberStateHash = selectedTeamMemberStateHash;
			}
			
			challengeClient.createSubmission(newSubmission, etag, teamId, memberStateHash, new AsyncCallback<Submission>() {			
				@Override
				public void onSuccess(Submission result) {
					//result is the updated submission
					String message = evaluation.getSubmissionReceiptMessage();
					if (message == null || message.length()==0)
						message = DisplayConstants.SUBMISSION_RECEIVED_TEXT;
					view.hideModal2();
					view.showSubmissionAcceptedDialogs(message);
				}
				
				@Override
				public void onFailure(Throwable caught) {
					if(!DisplayUtils.handleServiceException(caught, globalApplicationState, authenticationController.isLoggedIn(), view))
						view.showErrorMessage(caught.getMessage());
				}
			});
		} catch (RestServiceException e) {
			view.showErrorMessage(e.getMessage());
		}
	}
	
	
	public Widget asWidget() {
		return view.asWidget();
	}
}
