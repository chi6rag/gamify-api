package com.core.manager;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.core.constants.GameConstants;
import com.core.constants.GameConstants.GAME_DIFFICULTY_LEVEL;
import com.core.constants.GameConstants.GAME_STATE;
import com.core.domain.Option;
import com.core.domain.User;
import com.core.domain.knockout.GameInstance;
import com.core.domain.knockout.PlayerResponseLog;
import com.core.domain.knockout.PreviousQuestionLog;
import com.core.domain.lms.ExamSection;
import com.core.service.AnswerKeyService;
import com.core.service.GameInstanceService;

@Component
public class GameQueueManager {

	public static Map<Long, GameInstance> newGames = new ConcurrentHashMap<Long, GameInstance>();
	public static Map<Long, GameInstance> waitingForMorePlayersToJoinGames = new ConcurrentHashMap<Long, GameInstance>();
	public static Map<Long, GameInstance> readyGames = new ConcurrentHashMap<Long, GameInstance>();
	public static Map<Long, GameInstance> ongoingGames = new ConcurrentHashMap<Long, GameInstance>();
	public static Map<Long, GameInstance> finishedGames = new ConcurrentHashMap<Long, GameInstance>();

	public static Map<Long, GameInstance> expriredGames = new ConcurrentHashMap<Long, GameInstance>();

	public static Map<Long, GameInstance> playerGameMap = new ConcurrentHashMap<Long, GameInstance>();

	public static Map<Long, List<PreviousQuestionLog>> gameResponseLog = new ConcurrentHashMap<Long, List<PreviousQuestionLog>>();

	private static final Logger log = LoggerFactory
			.getLogger(GameQueueManager.class);

	private static AnswerKeyService answerKeyService;

	/**
	 * Sets the answerKeyServiceDao This method should never be called except by
	 * Spring
	 * 
	 * @param userAccessor
	 *            The user accessor to set
	 */
	@Autowired(required = true)
	public void setAnswerKeyService(AnswerKeyService answerKeyService) {
		GameQueueManager.answerKeyService = answerKeyService;
	}

	private static GameInstanceService gameInstanceService;

	/**
	 * Sets the answerKeyServiceDao This method should never be called except by
	 * Spring
	 * 
	 * @param userAccessor
	 *            The user accessor to set
	 */
	@Autowired(required = true)
	public void setGameInstanceService(GameInstanceService gameInstanceService) {
		GameQueueManager.gameInstanceService = gameInstanceService;
	}

	public static GameInstance getGameInstanceForPlayer(Long playerId) {
		return playerGameMap.get(playerId);
	}

	public static synchronized GameInstance joinGameInstanceIfUserAlreadyInGame(
			User user) {
		for (Long playerId : playerGameMap.keySet()) {

			if (playerId.equals(user.getId())) {
				GameInstance gi = playerGameMap.get(playerId);
				if (!gi.getState().equals(GAME_STATE.DONE)
						&& !gi.getState().equals(GAME_STATE.EXPIRED)) {
					return playerGameMap.get(playerId);
				}

			}

		}
		return null;
	}

	public static synchronized GameInstance createGameInstance(
			ExamSection examSection, User user) {
		GameInstance gi = null;
		if (waitingForMorePlayersToJoinGames.get(examSection.getId()) != null) {
			gi = waitingForMorePlayersToJoinGames.get(examSection.getId());
			if (!gi.isPlayerInGame(user.getId())) {
				gi.addPlayer(user);
				playerGameMap.put(user.getId(), gi);
				if (gi.getNumOfPlayers() == GameConstants.MAXIMUM_NUM_OF_PLAYERS_NEEDED) {
					gi.setState(GameConstants.GAME_STATE.READY);
					readyGames.put(gi.getId(), gi);
					waitingForMorePlayersToJoinGames
							.remove(examSection.getId());

				}
			}
			return gi;

		} else if (newGames.get(examSection.getId()) != null) {
			gi = GameQueueManager.newGames.get(examSection.getId());
			if (!gi.isPlayerInGame(user.getId())) {
				gi.addPlayer(user);

				// log.info("((((((((((((((((((((((((((BEFORE PUTTING USER TO PLAYER GAME MAP))))))))))))))))))))))))"+user.getId());;
				playerGameMap.put(user.getId(), gi);
				if (gi.getNumOfPlayers() == GameConstants.MINIMUM_NUM_OF_PLAYERS_NEEDED) {
					gi.setStartWaitTime(System.currentTimeMillis());
					gi.setState(GameConstants.GAME_STATE.WAITING);
					waitingForMorePlayersToJoinGames.put(examSection.getId(),
							gi);
					newGames.remove(examSection.getId());
				}
			}
			return gi;

		}

		else {
			gi = new GameInstance();
			gi.setDifficultyLevel(GAME_DIFFICULTY_LEVEL.EASY);
			gi.addPlayer(user);
			gi.setExamSection(examSection);
			gi.setState(GameConstants.GAME_STATE.NEW);
			gi = gameInstanceService.saveOrUpdate(gi);
			newGames.put(examSection.getId(), gi);
			playerGameMap.put(user.getId(), gi);
			return gi;
		}
	}

	public static synchronized boolean checkIfPlayerAlreadyInGame(User user) {
		GameInstance gi = playerGameMap.get(user.getId());
		if (gi != null
				&& GameConstants.GAME_STATE.ONGOING.equals(gi.getState())) {
			return true;
		} else
			return false;
	}

	public static synchronized void removePlayerFromGameIfQuitOrLoggedOutOrSessionExpired(
			User user) {
		GameInstance gi = playerGameMap.get(user.getId());
		if (gi != null) {
			gi.removePlayer(user);
			playerGameMap.remove(user.getId());
		}

		log.info("The userId removed is:" + user.getId());
	}

	public static synchronized GameInstance recordPlayerResponseToQuestion(
			Long userId, Long questionId, Long optionId,
			long secondsTakenToRespond) {
		GameInstance gi = playerGameMap.get(userId);
		if (gi != null && gi.getCurrentQuestion() != null
				&& gi.getCurrentQuestion().getId().equals(questionId)) {
			PlayerResponseLog prl = new PlayerResponseLog(gi.getPlayers().get(
					userId), new User(userId), new Option(optionId),
					secondsTakenToRespond);
			log.info("just before setting the option id:" + optionId
					+ " for player:" + userId + " for question with id:"
					+ questionId);
			gi.getPlayerResponsesToCurrentQuestion().put(userId, prl);
			if (answerKeyService.isCorrectAnswer(questionId, prl.getResponse())) {
				if (gi.getCurrentQuestionWinner() == null
						|| secondsTakenToRespond < gi
								.getBestTimeForCurrentQuestion()) {
					gi.setBestTimeForCurrentQuestion(secondsTakenToRespond);
					gi.setCurrentQuestionWinner(new User(userId));
				}
			}
		}
		return gi;
	}

	public static synchronized void decreaseLife(GameInstance gi, long userId) {
		gi.getPlayers().get(userId)
				.setNoOfLife(gi.getPlayers().get(userId).getNoOfLife() - 1);
		log.info(" Life decreased for userId " + userId + "  Life : "
				+ gi.getPlayers().get(userId).getNoOfLife());
		if (gi.getPlayers().get(userId).getNoOfLife() <= 0) {
			gi.removePlayer(new User(userId));
		}
	}

	private static synchronized void manageLife(GameInstance gi) {
		List<Long> playersToBeRemoved = new LinkedList<Long>();
		if (gi.getPlayers() != null) {
			for (long userId : gi.getPlayers().keySet()) {
				if (gi.getCurrentQuestionWinner() == null
						|| (gi.getCurrentQuestionWinner() != null && gi
								.getCurrentQuestionWinner().getId() != userId))
					gi.getPlayers()
							.get(userId)
							.setNoOfLife(
									gi.getPlayers().get(userId).getNoOfLife() - 1);
				log.info(" Life decreased for userId " + userId + "  Life : "
						+ gi.getPlayers().get(userId).getNoOfLife());
				if (gi.getPlayers().get(userId).getNoOfLife() <= 0) {
					playersToBeRemoved.add(userId);
				}
			}
		}
		gi.removePlayers(playersToBeRemoved);
	}

	public static synchronized void calculateScoresForPlayers(GameInstance gi) {

		if (gi.getPlayers() != null
				&& gi.getPlayerResponsesToCurrentQuestion() != null) {

			log.info("debug 6. Number of players are:" + gi.getPlayers().size());
			log.info("debug 7. NUmber of responses are:"
					+ gi.getPlayerResponsesToCurrentQuestion().size());
			if (gi.getPlayers().size() <= gi
					.getPlayerResponsesToCurrentQuestion().size()) {
				manageLife(gi);
				log.info("debug 8");
				/*
				 * Map<Long, PlayerResponseLog> prlLog = gi
				 * .getPlayerResponsesToCurrentQuestion();
				 * List<PlayerResponseLog> questionWinnerList = new
				 * ArrayList<PlayerResponseLog>(); for (Long userId :
				 * prlLog.keySet()) { PlayerResponseLog prl =
				 * prlLog.get(userId); if (answerKeyService.isCorrectAnswer(gi
				 * .getCurrentQuestion().getId(), prl.getResponse())) {
				 * questionWinnerList.add(prl); } else { gi.getPlayers()
				 * .get(userId) .setNoOfLife( gi.getPlayers().get(userId)
				 * .getNoOfLife() - 1); if
				 * (gi.getPlayers().get(userId).getNoOfLife() <= 0) {
				 * gi.removePlayer(new User(userId)); } } }
				 */

				/*
				 * if(gi.getPlayers().size() < 2){ log.info(
				 * "debug Game Done 99999999999999999999999999999999999999999999999999999999999999999998"
				 * ); QuestionManager.savePreviousQuestionLog(gi);
				 * gi.setStateToDone();
				 * GameQueueManager.finishedGames.put(gi.getId(), gi);
				 * GameQueueManager.ongoingGames.remove(gi.getId()); return; }
				 */
				log.info("debug 9");
				// Long winningPlayerId = null;
				/*
				 * if (questionWinnerList.size() > 0) {
				 * 
				 * Collections.sort(questionWinnerList); winningPlayerId =
				 * questionWinnerList.get(0).getPlayer() .getUser().getId(); for
				 * (PlayerResponseLog playerResponseLog : questionWinnerList) {
				 * Player winningPlayerCandidate = playerResponseLog
				 * .getPlayer(); if (!winningPlayerCandidate.getUser().getId()
				 * .equals(winningPlayerId)) {
				 * 
				 * winningPlayerCandidate .setNoOfLife(winningPlayerCandidate
				 * .getNoOfLife() - 1);
				 * 
				 * if (winningPlayerCandidate.getNoOfLife() <= 0) {
				 * 
				 * gi.removePlayer(winningPlayerCandidate .getUser()); //
				 * GameQueueManager
				 * .playerGameMap.remove(winningPlayerCandidate.getUser
				 * ().getId()); } } else {
				 * gi.setCurrentQuestionWinner(winningPlayerCandidate
				 * .getUser());
				 * gi.setBestTimeForCurrentQuestion(playerResponseLog
				 * .getTimeTakenToAnswer());
				 * 
				 * } } }
				 */

				log.info("debug 10");

				if (gi.getPlayers().size() < 2) {
					log.info("debug Game Done 99999999999999999999999999999999999999999999999999999999999999999998");
					QuestionManager.savePreviousQuestionLog(gi);
					gi.setStateToDone();
					gi.markGameWinner();
					GameQueueManager.finishedGames.put(gi.getId(), gi);
					GameQueueManager.ongoingGames.remove(gi.getId());
					return;

				}

				QuestionManager.savePreviousQuestionLog(gi);
				gi.resetCurrentQuestionWinnerAndBestTime();
				QuestionManager.attachQuestionToGameInstance(gi);
				log.info("----- New Question attached is -----------"
						+ gi.getCurrentQuestion().getId().toString());

			}
		}
	}
}
