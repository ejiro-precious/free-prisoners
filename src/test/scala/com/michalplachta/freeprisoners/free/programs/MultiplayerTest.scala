package com.michalplachta.freeprisoners.free.programs

import cats.data.EitherK
import cats.~>
import com.michalplachta.freeprisoners.PrisonersDilemma.{
  Guilty,
  Prisoner,
  Silence,
  Verdict
}
import com.michalplachta.freeprisoners.free.algebras.GameOps.Game
import com.michalplachta.freeprisoners.free.algebras.MatchmakingOps._
import com.michalplachta.freeprisoners.free.algebras.PlayerOps.Player
import com.michalplachta.freeprisoners.free.algebras.TimingOps.Timing
import com.michalplachta.freeprisoners.free.programs.Multiplayer.findOpponent
import com.michalplachta.freeprisoners.free.testinterpreters.GameTestInterpreter.GameState
import com.michalplachta.freeprisoners.free.testinterpreters.MatchmakingTestInterpreter.{
  DelayedPrisoner,
  MatchmakingState,
  MatchmakingStateA
}
import com.michalplachta.freeprisoners.free.testinterpreters.PlayerGameTestInterpreter.{
  PlayerGame,
  PlayerGameState
}
import com.michalplachta.freeprisoners.free.testinterpreters.{
  MatchmakingTestInterpreter,
  PlayerGameTestInterpreter,
  TimingTestInterpreter
}
import com.michalplachta.freeprisoners.states.PlayerState
import org.scalatest.{Matchers, WordSpec}

class MultiplayerTest extends WordSpec with Matchers {
  "Multiplayer program" should {
    "have matchmaking module" which {
      type TimedMatchmaking[A] = EitherK[Timing, Matchmaking, A]
      implicit val matchmakingOps: Matchmaking.Ops[TimedMatchmaking] =
        new Matchmaking.Ops[TimedMatchmaking]

      implicit val timingOps: Timing.Ops[TimedMatchmaking] =
        new Timing.Ops[TimedMatchmaking]

      val interpreter: TimedMatchmaking ~> MatchmakingStateA =
        new TimingTestInterpreter[MatchmakingStateA] or new MatchmakingTestInterpreter

      "is able to create a match when there is one opponent registered" in {
        val player = Prisoner("Player")
        val registeredOpponent = DelayedPrisoner(Prisoner("Opponent"), 0)

        val initialState =
          MatchmakingState(waitingPlayers = List(registeredOpponent),
                           joiningPlayer = None,
                           metPlayers = Set.empty)

        val opponent: Option[Prisoner] = findOpponent(player)
          .foldMap(interpreter)
          .runA(initialState)
          .value

        opponent should contain(registeredOpponent.prisoner)
      }

      "is able to create a match even when an opponent registers late" in {
        val player = Prisoner("Player")
        val registeredOpponent = DelayedPrisoner(Prisoner("Opponent"), 3)

        val initialState =
          MatchmakingState(waitingPlayers = List(registeredOpponent),
                           joiningPlayer = None,
                           metPlayers = Set.empty)

        val opponent: Option[Prisoner] = findOpponent(player)
          .foldMap(interpreter)
          .runA(initialState)
          .value

        opponent should contain(registeredOpponent.prisoner)
      }

      "is not able to create a match when there are no opponents" in {
        val player = Prisoner("Player")

        val opponent: Option[Prisoner] = findOpponent(player)
          .foldMap(interpreter)
          .runA(MatchmakingState.empty)
          .value

        opponent should be(None)
      }

      "keeps count of registered and unregistered players" in {
        val player = Prisoner("Player")

        val state: MatchmakingState = findOpponent(player)
          .foldMap(interpreter)
          .runS(MatchmakingState.empty)
          .value

        state.waitingPlayers.size should be(0)
        state.metPlayers should be(Set(player))
      }

      "waits for another player to join" in {
        val player = Prisoner("Player")
        val joiningOpponent = DelayedPrisoner(Prisoner("Opponent"), 0)

        val initialState = MatchmakingState(waitingPlayers = List.empty,
                                            joiningPlayer =
                                              Some(joiningOpponent),
                                            metPlayers = Set.empty)

        val opponent: Option[Prisoner] = findOpponent(player)
          .foldMap(interpreter)
          .runA(initialState)
          .value

        opponent should contain(joiningOpponent.prisoner)
      }

      "waits for another player who joins late" in {
        val player = Prisoner("Player")
        val lateJoiningOpponent = DelayedPrisoner(Prisoner("Opponent"), 10)

        val initialState = MatchmakingState(waitingPlayers = List.empty,
                                            joiningPlayer =
                                              Some(lateJoiningOpponent),
                                            metPlayers = Set.empty)

        val opponent: Option[Prisoner] = findOpponent(player)
          .foldMap(interpreter)
          .runA(initialState)
          .value

        opponent should contain(lateJoiningOpponent.prisoner)
      }
    }

    "have game module" which {
      implicit val playerOps = new Player.Ops[PlayerGame]
      implicit val gameOps = new Game.Ops[PlayerGame]
      implicit val timingOps = new Timing.Ops[PlayerGame]
      val interpreter = new PlayerGameTestInterpreter

      "is able to produce verdict if both players make decisions" in {
        val player = Prisoner("Player")
        val opponent = Prisoner("Opponent")

        val initialState =
          PlayerGameState(PlayerState(Set.empty,
                                      Map(player -> Guilty),
                                      Map.empty),
                          GameState(Map(opponent -> Silence)))
        val result: PlayerGameState = Multiplayer
          .playTheGame(player, opponent)
          .foldMap(interpreter)
          .runS(initialState)
          .value

        result.playerState.verdicts.get(player) should contain(Verdict(0))
      }

      "is not able to produce verdict if the opponent doesn't make a decision" in {
        val player = Prisoner("Player")
        val opponent = Prisoner("Opponent")

        val initialState =
          PlayerGameState(PlayerState(Set.empty,
                                      Map(player -> Guilty),
                                      Map.empty),
                          GameState(Map.empty))

        val result: PlayerGameState = Multiplayer
          .playTheGame(player, opponent)
          .foldMap(interpreter)
          .runS(initialState)
          .value

        result.playerState.verdicts should be(Map.empty)
      }

      "is able to produce verdict if the opponent makes a decision after some time" in {
        val player = Prisoner("Player")
        val opponent = Prisoner("Opponent")

        val initialState =
          PlayerGameState(PlayerState(Set.empty,
                                      Map(player -> Guilty),
                                      Map.empty),
                          GameState(Map(opponent -> Guilty), delayInCalls = 10))

        val result: PlayerGameState = Multiplayer
          .playTheGame(player, opponent)
          .foldMap(interpreter)
          .runS(initialState)
          .value

        result.playerState.verdicts.get(player) should contain(Verdict(3))
      }
    }
  }
}