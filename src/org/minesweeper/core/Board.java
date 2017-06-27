package org.minesweeper.core;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Encapsulates a MineSweeper game board.
 */
public class Board implements Serializable, Cloneable {
    private final int width, height;
    private final Cell[][] board;
    private final Cell[] mines;
    private int callsToUpdate, difficulty;

    public Board(final int width, final int height, final float mineFraction) {
        if (mineFraction < 0 || mineFraction > 1) {
            throw new IllegalArgumentException("Fraction of mines can only be between 0 & 1 : " + mineFraction);
        }
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Illegal board dimensions : " + width + " , " + height);
        }
        board = new Cell[height][width];
        this.height = board.length;
        this.width = board[0].length;
        mines = new Cell[Math.round(mineFraction * getHeight() * getWidth())];
        callsToUpdate = 0;
        difficulty = get3BValue();
        initBoard();
    }

    public void initBoard() {
        for (int i = 0; i < getHeight(); ++i) {
            for (int j = 0; j < getWidth(); ++i) {
                board[i][j] = new Cell(j, i);
            }
        }
    }

    public Cell[][] getBoard() {
        return board;
    }

    public float getMineFraction() {
        return ((float) getMinesCount()) / (getHeight() * getWidth());
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getMinesCount() {
        return getMines().length;
    }

    private Cell[] getMines() {
        return mines;
    }

    private Cell get(Cell cell) {
        return get(board, cell);
    }

    private Cell get(Cell[][] board, Cell cell) {
        return get(board, cell.getX(), cell.getY());
    }

    private void set(Cell[][] board, int x, int y, int neighbouringMineCount) {
        get(board, x, y).setNeighbouringMineCount(neighbouringMineCount);
    }

    private Cell get(int x, int y) {
        return get(board, x, y);
    }

    private Cell get(Cell[][] board, int x, int y) {
        y = bounded(y, getHeight());
        return board[y][bounded(x, getWidth())];
    }

    private static int bounded(int ptr, int size) {
        return (ptr < 0) ? Math.abs(size + ptr) % size : ((ptr >= size) ? (ptr % size) : ptr);
    }

    private void set(Cell[][] board, Cell cell, int neighbouringMineCount) {
        set(board, cell.getX(), cell.getY(), neighbouringMineCount);
    }

    private boolean isClear(Cell[][] board, Cell cell) {
        return isClear(board, cell.getX(), cell.getY());
    }

    private boolean isClear(Cell[][] board, int x, int y) {
        return get(board, x, y).isClear();
    }

    private boolean hasMine(Cell[][] board, int x, int y) {
        return get(board, x, y).hasMine();
    }

    private boolean hasMine(Cell cell) {
        return hasMine(board, cell);
    }

    private boolean hasMine(Cell[][] board, Cell cell) {
        return hasMine(board, cell.getX(), cell.getY());
    }

    private List<Cell> getMarkedCells() {
        List<Cell> marked = new ArrayList<>(getHeight() * board[0].length);
        for (Cell[] row : board) {
            for (Cell cell : row) {
                if (isMarked(cell)) {
                    marked.add(new Cell(cell, true));
                }
            }
        }
        return marked;
    }

    private Cell[] getNeighbours(Cell cell) {
        return getNeighbours(board, cell);
    }

    private Cell[] getNeighbours(Cell[][] board, Cell cell) {
        cell = get(board, cell);
        Cell[] neighbours = new Cell[Cell.MAX_NEIGHBOURS];
        for (int y = cell.getY() - 1, count = 0; y <= cell.getY() + 1; ++y) {
            for (int x = cell.getX() - 1; x <= cell.getX() + 1 && count < neighbours.length; ++x) {
                if (!(cell.getX() == x || cell.getY() == y)) {
                    neighbours[count++] = get(board, x, y);
                }
            }
        }
        return neighbours;
    }

    private boolean isLost(Cell selected) {
        boolean lost = hasMine(selected);
        if (lost) {
            reveal(selected);
            //reveal all the mines, which hadn't been revealed
            revealAll();
        }
        return lost;
    }

    private void revealAll() {
        for (Cell[] row : board) {
            for (Cell cell : row) {
                if (!hasBeenRevealed(cell)) {
                    reveal(get(cell));
                }
            }
        }
    }

    private void updateCellsWithMineCountAndReveal(Cell cell) {
        updateCellsWithMineCountAndReveal(board, cell);
    }

    private void updateCellsWithMineCountAndReveal(Cell[][] board, Cell cell) {
        reveal(cell);
        //maintain the suspense
        if (hasMine(board, cell) || isMarked(board, cell)) {
            return;
        }
        for (Cell neighbour : getNeighbours(board, cell)) {
            if (cell.getNumberOfNeighboursWithMines() < 0 ||
                    cell.getNumberOfNeighboursWithMines() > Cell.MAX_NEIGHBOURS) {
                break;
            }
            if (hasMine(board, neighbour)) {
                //update mine count
                set(board, cell, cell.getNumberOfNeighboursWithMines() + 1);
            } else if (isClear(board, neighbour) && (!(hasBeenRevealed(board, neighbour) || isMarked(board, neighbour) || hasMine(board, neighbour)))) {
                //recursively update mine counts of all clear neighbours
                updateCellsWithMineCountAndReveal(board, neighbour);
            }
        }
    }

    /**
     * Use for calculating scores:
     * <ol>
     * <li>Divide by total number of clicks (implemented)</li>
     * <li>Divide by time taken (implemented)</li>
     * </ol>
     *
     * @return an objective measure of the difficulty of this minesweeper board
     */
    public int get3BValue() {
        //initialization
        Cell[][] backup = new Cell[getHeight()][getWidth()];
        for (int i = 0; i < getHeight(); ++i) {
            for (int j = 0; j < getWidth(); ++j) {
                backup[i][j] = new Cell(get(j, i), true);
            }
        }
        //processing
        for (Cell[] row : backup) {
            for (Cell cell : row) {
                updateCellsWithMineCountAndReveal(backup, cell);
            }
        }
        //calculation
        int value = 0;
        for (Cell[] row : backup) {
            for (Cell cell : row) {
                if (isClear(backup, cell)) {
                    if (!isMarked(backup, cell)) {
                        mark(backup, cell);
                        ++value;
                        floodFillMark(backup, cell);
                    }
                }
                if (!(isMarked(backup, cell) || hasMine(backup, cell))) {
                    ++value;
                }
            }
        }
        return value;
    }

    public int getDifficulty() {
        return difficulty;
    }

    private void floodFillMark(Cell[][] backup, Cell cell) {
        Cell[] neighbours = getNeighbours(backup, cell);
        for (Cell neighbour : neighbours) {
            if (!isMarked(backup, neighbour)) {
                mark(backup, neighbour);
                if (isClear(backup, neighbour)) {
                    floodFillMark(backup, neighbour);
                }
            }
        }
    }

    private boolean isWon() {
        boolean win = true;
        for (Cell[] row : board) {
            for (Cell cell : row) {
                if (!(hasMine(cell) || hasBeenRevealed(cell))) {
                    win = false;
                    break;
                }
            }
        }
        win |= getMarkedCells().containsAll(Arrays.asList(mines));
        if (win) {
            revealAll();
        }
        return win;
    }

    private int getClicks() {
        return callsToUpdate;
    }

    /**
     * The click-based score, depending on number of calls to {@link #update(Cell)}.
     * <br>
     * Lower is better. Usually value is more than 1 at the end of the game if it has been won,
     * and less than 1 if it has been lost.
     * <br>
     * Multiply this with time taken for completion and round to get presentable score.
     * <br>
     * For presentable score involving time, lower is better.
     *
     * @return the click-based score
     */
    public double getClickBasedScore() {
        return ((double) getClicks()) / get3BValue();
    }

    /**
     * Guaranteed to be non-negative.
     *
     * @param timeTaken the time taken till now
     * @return the presentable score.
     */
    public int getPresentableScore(int timeTaken) {
        return Math.round((float) (getClickBasedScore() * timeTaken));
    }

    private void reveal(Cell cell) {
        reveal(cell.getX(), cell.getY());
    }

    private void reveal(int x, int y) {
        get(x, y).reveal();
    }

    private boolean hasBeenRevealed(Cell cell) {
        return hasBeenRevealed(board, cell);
    }

    private boolean hasBeenRevealed(Cell[][] board, Cell cell) {
        return hasBeenRevealed(board, cell.getX(), cell.getY());
    }

    private boolean hasBeenRevealed(Cell[][] board, int x, int y) {
        return get(board, x, y).hasBeenRevealed();
    }

    private void mark(Cell[][] board, Cell cell) {
        mark(board, cell.getX(), cell.getY());
    }

    private void mark(Cell[][] board, int x, int y) {
        get(board, x, y).mark();
    }

    private boolean isMarked(Cell cell) {
        return isMarked(board, cell);
    }

    private boolean isMarked(Cell[][] board, Cell cell) {
        return isMarked(board, cell.getX(), cell.getY());
    }

    private boolean isMarked(Cell[][] board, int x, int y) {
        return get(board, x, y).isMarked();
    }

    public Status update(Cell selected) {
        if (callsToUpdate == 0) {
            plantMines(selected);
        }
        ++callsToUpdate;
        if (isLost(selected)) {
            return Status.LOST;
        } else if (isWon()) {
            reveal(selected);
            return Status.WON;
        } else {
            updateCellsWithMineCountAndReveal(selected);
            return Status.CONTINUING;
        }
    }

    /**
     * Call this before updating neighbouring mines count for the first time.
     *
     * @param initialCell the initial cell selected, which may not have a mine.
     */
    public void plantMines(Cell initialCell) {
        for (int counter = 0; counter < getMinesCount(); ) {
            //get a random cell
            Cell randomCell = get(randInt(getHeight()), randInt(getWidth()));
            //protect a (valid) initial cell, and don't make it repeat itself (mine locations should be unique)
            if ((initialCell == null || (!randomCell.equals(initialCell))) && (!hasMine(randomCell))) {
                //add the random mine-containing cell to the mine list
                mines[counter] = new Cell(randomCell, true);
                mines[counter].setMine();
                //update the counter
                counter += randomCell.setMine();
            }
        }
    }

    private static int randInt(int max) {
        return randInt(0, max - 1);
    }

    private static int randInt(int min, int max) {
        return min + new Random().nextInt(Math.abs(max-min));
    }
    public enum Status {
        WON, LOST, CONTINUING
    }
}