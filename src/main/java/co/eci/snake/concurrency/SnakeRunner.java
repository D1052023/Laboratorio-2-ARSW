package co.eci.snake.concurrency;

import co.eci.snake.core.Board;
import co.eci.snake.core.Direction;
import co.eci.snake.core.GameState;
import co.eci.snake.core.Position;
import co.eci.snake.core.Snake;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class SnakeRunner implements Runnable {
  private final Snake snake;
  private final Board board;
  private final Object pauseLock;
  private final AtomicReference<GameState> gameState;
  private final int baseSleepMs = 80;
  private final int turboSleepMs = 40;
  private int turboTicks = 0;
  private final List<Snake> snakes;

  public SnakeRunner(Snake snake, Board board, Object pauseLock, AtomicReference<GameState> gameState,
      List<Snake> snakes) {
    this.snake = snake;
    this.board = board;
    this.pauseLock = pauseLock;
    this.gameState = gameState;
    this.snakes = snakes;
  }

  @Override
  public void run() {
    try {
      while (!Thread.currentThread().isInterrupted() && snake.isAlive()) {
        synchronized (pauseLock) {
          while (gameState.get() == GameState.PAUSED) {
            pauseLock.wait();
          }
        }
        maybeTurn();
        boolean dead = false;
        synchronized (board) {
          var head = snake.head();
          var dir = snake.direction();
          var next = new Position(
              head.x() + dir.dx, head.y() + dir.dy).wrap(board.width(), board.height());

          if (snake.snapshot().contains(next)) {
            snake.die();
            dead = true;
          }
          for (Snake other : snakes) {
            if (other != snake && other.isAlive() && other.snapshot().contains(next)) {
              snake.die();
              dead = true;
            }
          }
          if (dead)
            break;
          var res = board.step(snake);
          if (res == Board.MoveResult.HIT_OBSTACLE) {
            randomTurn();
          } else if (res == Board.MoveResult.ATE_TURBO) {
            turboTicks = 100;
          }
        }

        int sleep = (turboTicks > 0) ? turboSleepMs : baseSleepMs;
        if (turboTicks > 0)
          turboTicks--;
        Thread.sleep(sleep);
      }
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    }
  }

  private void maybeTurn() {
    double p = (turboTicks > 0) ? 0.05 : 0.10;
    if (ThreadLocalRandom.current().nextDouble() < p)
      randomTurn();
  }

  private void randomTurn() {
    var dirs = Direction.values();
    snake.turn(dirs[ThreadLocalRandom.current().nextInt(dirs.length)]);
  }
}
