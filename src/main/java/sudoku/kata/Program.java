package sudoku.kata;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.stream.Collectors.*;

public class Program {

    static void play() {
        play(new Random());
    }

    static void play(Random rng) {

        State solutionState = constructBoardToBeSolved(rng);
        printSolutionState(solutionState);

        State startingState = generateStartingState(rng, solutionState);
        printStartingState(startingState);

        printDivider();

        State state = startingState.copy();

        boolean changeMade = true;
        while (changeMade) {
            changeMade = false;

            Candidates candidates = new Candidates(state);

            boolean stepChangeMade = true;
            while (stepChangeMade) {

                Change change = pickACellWithOnlyOneCandidateDigitLeft(rng, candidates);

                if (change == null) {
                    change = pickACellInAGroupThatOnlyCanHaveADigitInOnePlace(rng, candidates);
                }

                if (change != null) {
                    state.set(change.getCell(), change.getDigit());
                    candidates.get(change.getCell()).setNoCandidates(); // stepChange - but not used?
                    System.out.println(change.getReason());
                    changeMade = true;
                }

                stepChangeMade = false;

                if (!changeMade) {

                    //region Try to find pairs of digits in the same row/column/block and remove them from other colliding cells
                    List<MaskGroup> twoDigitGroups = groupsWithPairsOfCellsWithSameTwoDigitCandidates(candidates);

                    if (!twoDigitGroups.isEmpty()) {
                        for (var twoDigitGroup : twoDigitGroups) {
                            var cellsToCleanUp =
                                    twoDigitGroup.stream()
                                            .filter(cell -> !candidates.get(cell).getMask().equals(twoDigitGroup.getMask())
                                                    && (candidates.get(cell).getMask().overlappingWith(twoDigitGroup.getMask()).get()) > 0)
                                            .collect(toList());

                            if (!cellsToCleanUp.isEmpty()) {
                                var maskCells =
                                        twoDigitGroup.stream()
                                                .filter(cell ->
                                                        candidates.get(cell).getMask().equals(twoDigitGroup.getMask()))
                                                .collect(toList());

                                List<Integer> digitsInGroup = twoDigitGroup.getMask().digits();
                                System.out.println(
                                        "Values " + digitsInGroup.get(0) + " and " + digitsInGroup.get(1) + " in " + twoDigitGroup.getDescription() +
                                                " are in cells " + maskCells.get(0) +
                                                " and " + maskCells.get(1) + ".");

                                for (var cell : cellsToCleanUp) {
                                    Candidate candidate = candidates.get(cell);
                                    Mask maskToRemove = candidate.getMask().overlappingWith(twoDigitGroup.getMask());

                                    String valuesReport = maskToRemove.digits().stream().map(Object::toString).collect(joining(", "));
                                    System.out.println(valuesReport + " cannot appear in " + cell + ".");

                                    candidate.setMask(candidate.getMask().minus(twoDigitGroup.getMask()));
                                    stepChangeMade = true;
                                }
                            }
                        }
                    }
                    //endregion
                }


                if (!changeMade && !stepChangeMade) {
                    //region Try to find groups of digits of size N which only appear in N cells within row/column/block
                    // When a set of N digits only appears in N cells within row/column/block, then no other digit can appear in the same set of cells
                    // All other candidates can then be removed from those cells

                    var groupsWithNMasks =
                            Masks.nMasks.stream()
                                    .map(mask -> CellGroup.all().stream()
                                            .filter(group -> group.stream().allMatch(cell -> state.get(cell) == 0 || !mask.matches(Masks.maskForDigit(state.get(cell)))))
                                            .map(group -> new MaskGroup(mask, group))
                                            .filter(group -> group.cellsWithMask(state, candidates, mask).size() == group.getMask().candidatesCount())
                                            .collect(toList()))
                                    .flatMap(Collection::stream)
                                    .collect(toList());


                    for (var groupWithNMasks : groupsWithNMasks) {
                        Mask mask = groupWithNMasks.getMask();

                        if (groupWithNMasks.stream()
                                .anyMatch(cell ->
                                        candidates.get(cell).matchesMask(mask)
                                                && candidates.get(cell).matchesMask(mask.inverted()))) {
                            StringBuilder message = new StringBuilder();
                            message.append("In " + groupWithNMasks.getDescription() + " values ");

                            String separator = "";
                            int temp = mask.get();
                            int curValue = 1;
                            while (temp > 0) {
                                if ((temp & 1) > 0) {
                                    message.append(separator + curValue);
                                    separator = ", ";
                                }
                                temp = temp >> 1;
                                curValue += 1;
                            }

                            message.append(" appear only in cells");
                            for (var cell : groupWithNMasks.cellsWithMask(state, candidates, mask)) {
                                message.append(" " + cell);
                            }

                            message.append(" and other values cannot appear in those cells.");

                            System.out.println(message.toString());
                        }

                        for (var cell : groupWithNMasks.cellsWithMask(state, candidates, mask)) {
                            int maskToClear = candidates.get(cell).getMask().get() & ~groupWithNMasks.getMask().get();
                            if (maskToClear == 0)
                                continue;

                            candidates.get(cell).setMask(candidates.get(cell).getMask().overlappingWith(groupWithNMasks.getMask()));
                            stepChangeMade = true;

                            int valueToClear = 1;

                            String separator = "";
                            StringBuilder message = new StringBuilder();

                            while (maskToClear > 0) {
                                if ((maskToClear & 1) > 0) {
                                    message.append(separator + valueToClear);
                                    separator = ", ";
                                }
                                maskToClear = maskToClear >> 1;
                                valueToClear += 1;
                            }

                            message.append(" cannot appear in cell " + cell + ".");
                            System.out.println(message.toString());
                        }
                    }
                    //endregion
                }

            }
            //region Final attempt - look if the board has multiple solutions
            if (!changeMade) {
                // This is the last chance to do something in this iteration:
                // If this attempt fails, board will not be entirely solved.

                // Try to see if there are pairs of values that can be exchanged arbitrarily
                // This happens when board has more than one valid solution

                Queue<Candidate> candidateQueue1 = new LinkedList<>();
                Queue<Candidate> candidateQueue2 = new LinkedList<>();
                Queue<Integer> candidateDigit1 = new LinkedList<>();
                Queue<Integer> candidateDigit2 = new LinkedList<>();

                for (Candidate candidateI : candidates) {
                    if (candidateI.candidateDigitsCount() == 2) {

                        var digits = candidateI.getMask().digits();

                        for (int j = candidates.indexOf(candidateI) + 1; j < candidates.size(); j++) {
                            Candidate candidateJ = candidates.get(j);
                            if (candidates.get(j).getMask().equals(candidateI.getMask())) {

                                if (Cell.sharesACellGroup(candidateI.getCell(), candidateJ.getCell())) {
                                    candidateQueue1.add(candidateI);
                                    candidateQueue2.add(candidateJ);
                                    candidateDigit1.add(digits.get(0));
                                    candidateDigit2.add(digits.get(1));
                                }
                            }
                        }
                    }
                }

                // At this point we have the lists with pairs of cells that might pick one of two digits each
                // Now we have to check whether that is really true - does the board have two solutions?

                List<Cell> cellList1 = new ArrayList<>();
                List<Cell> cellList2 = new ArrayList<>();
                List<Integer> digitList1 = new ArrayList<>();
                List<Integer> digitList2 = new ArrayList<>();

                while (!candidateQueue1.isEmpty()) {
                    Candidate candidate1 = candidateQueue1.remove();
                    Candidate candidate2 = candidateQueue2.remove();
                    int digit1 = candidateDigit1.remove();
                    int digit2 = candidateDigit2.remove();

                    State alternateState = state.copy();

                    if (solutionState.get(candidate1.getCell()) == digit1) {
                        alternateState.set(candidate1.getCell(), digit2);
                        alternateState.set(candidate2.getCell(), digit1);
                    } else {
                        alternateState.set(candidate1.getCell(), digit1);
                        alternateState.set(candidate2.getCell(), digit2);
                    }

                    {
                        // What follows below is a complete copy-paste of the solver which appears at the beginning of this method
                        // However, the algorithm couldn't be applied directly and it had to be modified.
                        // Implementation below assumes that the board might not have a solution.

                        Stack<State> stateStack = new Stack<>();
                        Stack<Cell> cellStack = new Stack<>();
                        Stack<boolean[]> usedDigitsStack = new Stack<>();
                        Stack<Integer> lastDigitStack = new Stack<>();

                        String command = "expand";

                        while (!command.equals("complete") && !command.equals("fail")) {
                            if (command.equals("expand")) {
                                final State currentState;

                                if (!stateStack.isEmpty()) {
                                    currentState = stateStack.peek().copy();
                                } else {
                                    currentState = alternateState.copy();
                                }

                                Cell bestCell = null;

                                boolean[] bestUsedDigits = null;
                                int bestCandidatesCount = -1;
                                int bestRandomValue = -1;
                                boolean containsUnsolvableCells = false;

                                for (var cell : Cell.cells()) {
                                    if (currentState.get(cell) == 0) {

                                        var digitUsedMask =
                                                cell.allSiblings().stream()
                                                        .mapToInt(sibling -> Masks.maskForDigit((int) currentState.get(sibling)).get())
                                                        .reduce(0, (digitsMask, digitMask) -> digitsMask | digitMask);

                                        boolean[] isDigitUsed = new boolean[9];

                                        for (int i = 0; i < 9; i++) {
                                            isDigitUsed[i] = (digitUsedMask & Masks.maskForDigit(i + 1).get()) != 0;
                                        }

                                        int candidatesCount = (int) (IntStream.range(0, isDigitUsed.length)
                                                .mapToObj(idx -> isDigitUsed[idx]).filter(used -> !used).count());

                                        if (candidatesCount == 0) {
                                            containsUnsolvableCells = true;
                                            break;
                                        }

                                        int randomValue = rng.nextInt();

                                        if (bestCandidatesCount < 0 ||
                                                candidatesCount < bestCandidatesCount ||
                                                (candidatesCount == bestCandidatesCount && randomValue < bestRandomValue)) {
                                            bestCell = cell;
                                            bestUsedDigits = isDigitUsed;
                                            bestCandidatesCount = candidatesCount;
                                            bestRandomValue = randomValue;
                                        }

                                    } // for (index = 0..81)
                                }

                                if (!containsUnsolvableCells) {
                                    stateStack.push(currentState);
                                    cellStack.push(bestCell);
                                    usedDigitsStack.push(bestUsedDigits);
                                    lastDigitStack.push(0); // No digit was tried at this position
                                }

                                // Always try to move after expand
                                command = "move";

                            } // if (command == "expand")
                            else if (command.equals("collapse")) {
                                stateStack.pop();
                                cellStack.pop();
                                usedDigitsStack.pop();
                                lastDigitStack.pop();

                                if (!stateStack.empty())
                                    command = "move"; // Always try to move after collapse
                                else
                                    command = "fail";
                            } else if (command.equals("move")) {

                                Cell cellToMove = cellStack.peek();
                                int digitToMove = lastDigitStack.pop();

                                boolean[] usedDigits = usedDigitsStack.peek();
                                State currentState = stateStack.peek();

                                int movedToDigit = digitToMove + 1;
                                while (movedToDigit <= 9 && usedDigits[movedToDigit - 1])
                                    movedToDigit += 1;

                                if (digitToMove > 0) {
                                    usedDigits[digitToMove - 1] = false;
                                    currentState.set(cellToMove, 0);
                                }

                                if (movedToDigit <= 9) {
                                    lastDigitStack.push(movedToDigit);
                                    usedDigits[movedToDigit - 1] = true;
                                    currentState.set(cellToMove, movedToDigit);

                                    if (Arrays.stream(currentState.getState()).anyMatch(digit -> digit == 0))
                                        command = "expand";
                                    else
                                        command = "complete";
                                } else {
                                    // No viable candidate was found at current position - pop it in the next iteration
                                    lastDigitStack.push(0);
                                    command = "collapse";
                                }
                            } // if (command == "move")

                        } // while (command != "complete" && command != "fail")

                        if (command.equals("complete")) {   // Board was solved successfully even with two digits swapped
                            cellList1.add(candidate1.getCell());
                            cellList2.add(candidate2.getCell());
                            digitList1.add(digit1);
                            digitList2.add(digit2);
                        }
                    }
                } // while (!candidateIndex1.empty())

                if (!cellList1.isEmpty()) {
                    int pos = rng.nextInt(cellList1.size());

                    Cell cell1 = cellList1.get(pos);
                    Cell cell2 = cellList2.get(pos);

                    String description = "";

                    if (cell1.getRow() == cell2.getRow()) {
                        description = "row #" + (cell1.getRow() + 1);
                    } else if (cell1.getColumn() == cell2.getColumn()) {
                        description = "column #" + (cell1.getColumn() + 1);
                    } else {
                        description = "block (" + (cell1.getBlockRow() + 1) + ", " + (cell1.getBlockCol() + 1) + ")";
                    }

                    state.set(cell1, solutionState.get(cell1));
                    state.set(cell2, solutionState.get(cell2));
                    candidates.get(cell1).setNoCandidates();
                    candidates.get(cell2).setNoCandidates();
                    changeMade = true;

                    System.out.println("Guessing that " + digitList1.get(pos) + " and " + digitList2.get(pos) + " are arbitrary in " + description + " (multiple solutions): Pick " + solutionState.get(cell1) + "->" + cell1 + ", " + solutionState.get(cell2) + "->" + cell2 + ".");
                }
            }
            //endregion

            if (changeMade) {
                printState(state);
            }
        }
    }

    private static void printSolutionState(State solutionState) {
        System.out.println();
        System.out.println("Final look of the solved board:");
        new Board(solutionState).printBoard();
    }

    private static void printStartingState(State startingState) {
        System.out.println();
        System.out.println("Starting look of the board to solve:");
        new Board(startingState).printBoard();
    }

    private static void printDivider() {
        System.out.println();
        System.out.println("=".repeat(80));
        System.out.println();
    }

    private static void printState(State state) {
        Board board = new Board(state);
        board.printBoard();
        board.printCode();
        System.out.println();
    }

    private static State generateStartingState(Random rng, State solutionState) {

        // Now pick subset of digits as the starting position.
        int remainingDigits = 30;
        int maxRemovedPerBlock = 6;
        int[][] removedPerBlock = new int[3][3];
        int[] positions = IntStream.range(0, 9 * 9).toArray();

        int removedPos = 0;
        while (removedPos < 9 * 9 - remainingDigits) {
            int curRemainingDigits = positions.length - removedPos;
            int indexToPick = removedPos + rng.nextInt(curRemainingDigits);

            Cell cell = Cell.of(positions[indexToPick]);
            if (removedPerBlock[cell.getBlockRow()][cell.getBlockCol()] >= maxRemovedPerBlock)
                continue;
            removedPerBlock[cell.getBlockRow()][cell.getBlockCol()] += 1;

            swap(positions, removedPos, indexToPick);

            removedPos++;
        }

        State startingState = solutionState.copy();

        for (int i = 0; i < removedPos; i++) {
            startingState.set(positions[i], 0);
        }
        return startingState;
    }

    private static State constructBoardToBeSolved(Random rng) {

        // Top element is current state of the board
        Stack<State> stateStack = new Stack<>();

        // Top elements are (row, col) of cell which has been modified compared to previous state
        Stack<Cell> cellStack = new Stack<>();

        // Top element indicates candidate digits (those with False) for (row, col)
        Stack<boolean[]> usedDigitsStack = new Stack<>();

        // Top element is the value that was set on (row, col)
        Stack<Integer> lastDigitStack = new Stack<>();

        // Indicates operation to perform next
        // - expand - finds next empty cell and puts new state on stacks
        // - move - finds next candidate number at current pos and applies it to current state
        // - collapse - pops current state from stack as it did not yield a solution
        String command = "expand";
        while (stateStack.size() <= 9 * 9) {
            if (command.equals("expand")) {
                final State currentState;

                if (!stateStack.isEmpty()) {
                    currentState = stateStack.peek().copy();
                } else {
                    currentState = new State(new int[9 * 9]);
                }

                Cell bestCell = null;
                boolean[] bestUsedDigits = null;
                int bestCandidatesCount = -1;
                int bestRandomValue = -1;
                boolean containsUnsolvableCells = false;

                for (var cell : Cell.cells()) {
                    if (currentState.get(cell) == 0) {

                        var digitUsedMask =
                                cell.allSiblings().stream()
                                        .mapToInt(sibling -> Masks.maskForDigit(currentState.get(sibling)).get())
                                        .reduce(0, (digitsMask, digitMask) -> digitsMask | digitMask);

                        boolean[] isDigitUsed = new boolean[9];

                        for (int i = 0; i < 9; i++) {
                            isDigitUsed[i] = (digitUsedMask & Masks.maskForDigit(i + 1).get()) != 0;
                        }

                        int candidatesCount = (int) (IntStream.range(0, isDigitUsed.length)
                                .mapToObj(idx -> isDigitUsed[idx]).filter(used -> !used).count());

                        if (candidatesCount == 0) {
                            containsUnsolvableCells = true;
                            break;
                        }

                        int randomValue = rng.nextInt();

                        if (bestCandidatesCount < 0 ||
                                candidatesCount < bestCandidatesCount ||
                                (candidatesCount == bestCandidatesCount && randomValue < bestRandomValue)) {
                            bestCell = cell;
                            bestUsedDigits = isDigitUsed;
                            bestCandidatesCount = candidatesCount;
                            bestRandomValue = randomValue;
                        }

                    } // for (index = 0..81)
                }

                if (!containsUnsolvableCells) {
                    stateStack.push(currentState);
                    cellStack.push(bestCell);
                    usedDigitsStack.push(bestUsedDigits);
                    lastDigitStack.push(0); // No digit was tried at this position
                }

                // Always try to move after expand
                command = "move";

            } // if (command == "expand")
            else if (command.equals("collapse")) {
                stateStack.pop();
                cellStack.pop();
                usedDigitsStack.pop();
                lastDigitStack.pop();

                command = "move";   // Always try to move after collapse
            } else if (command.equals("move")) {

                Cell cellToMove = cellStack.peek();
                int digitToMove = lastDigitStack.pop();

                boolean[] usedDigits = usedDigitsStack.peek();
                State currentState = stateStack.peek();

                int movedToDigit = digitToMove + 1;
                while (movedToDigit <= 9 && usedDigits[movedToDigit - 1])
                    movedToDigit += 1;

                if (digitToMove > 0) {
                    usedDigits[digitToMove - 1] = false;
                    currentState.set(cellToMove, 0);
                }

                if (movedToDigit <= 9) {
                    lastDigitStack.push(movedToDigit);
                    usedDigits[movedToDigit - 1] = true;
                    currentState.set(cellToMove, movedToDigit);

                    // Next possible digit was found at current position
                    // Next step will be to expand the state
                    command = "expand";
                } else {
                    // No viable candidate was found at current position - pop it in the next iteration
                    lastDigitStack.push(0);
                    command = "collapse";
                }
            } // if (command == "move")
        }

        return stateStack.peek();
    }

    private static Change pickACellWithOnlyOneCandidateDigitLeft(Random rng, Candidates candidates) {

        List<Candidate> singleCandidates = candidates.stream()
                .filter(candidate -> candidate.candidateDigitsCount() == 1)
                .collect(toList());

        if (singleCandidates.isEmpty()) {
            return null;
        }

        Candidate singleCandidate = singleCandidates.get(rng.nextInt(singleCandidates.size()));
        int digit = singleCandidate.singleDigit();

        String reason = String.format("%s can only contain %s.", singleCandidate.getCell(), digit);

        return Change.changeWithReason(CandidateChange.setDigit(singleCandidate.getCell(), digit), reason);
    }

    private static Change pickACellInAGroupThatOnlyCanHaveADigitInOnePlace(Random rng, Candidates candidates) {
        //region Try to find a number which can only appear in one place in a row/column/block
        List<String> groupDescriptions = new ArrayList<>();
        List<CandidateChange> candidateChange = new ArrayList<>();

        for (int digit = 1; digit <= 9; digit++) {
            for (int cellGroup = 0; cellGroup < 9; cellGroup++) {

                int rowNumberCount = 0;
                Cell rowCandidate = null;
                for (Cell cell : CellGroup.rows().get(cellGroup)) {
                    if (candidates.get(cell).hasCandidateDigit(digit)) {
                        rowNumberCount += 1;
                        rowCandidate = cell;
                    }
                }

                if (rowNumberCount == 1) {
                    groupDescriptions.add("Row #" + (cellGroup + 1));
                    candidateChange.add(CandidateChange.setDigit(rowCandidate, digit));
                }

                int colNumberCount = 0;
                Cell colCandidate = null;
                for (Cell cell : CellGroup.columns().get(cellGroup)) {
                    if (candidates.get(cell).hasCandidateDigit(digit)) {
                        colNumberCount += 1;
                        colCandidate = cell;
                    }
                }

                if (colNumberCount == 1) {
                    groupDescriptions.add("Column #" + (cellGroup + 1));
                    candidateChange.add(CandidateChange.setDigit(colCandidate, digit));
                }

                int blockNumberCount = 0;
                Cell blockCandidate = null;
                for (Cell cell : CellGroup.blocks().get(cellGroup)) {
                    if (candidates.get(cell).hasCandidateDigit(digit)) {
                        blockNumberCount += 1;
                        blockCandidate = cell;
                    }
                }

                if (blockNumberCount == 1) {
                    int blockRow = cellGroup / 3;
                    int blockCol = cellGroup % 3;

                    groupDescriptions.add("Block (" + (blockRow + 1) + ", " + (blockCol + 1) + ")");
                    candidateChange.add(CandidateChange.setDigit(blockCandidate, digit));
                }
            } // for (cellGroup = 0..8)
        } // for (digit = 1..9)

        if (candidateChange.size() > 0) {
            int index = rng.nextInt(candidateChange.size());
            String description = groupDescriptions.get(index);
            CandidateChange chosenChange = candidateChange.get(index);

            return Change.changeWithReason(chosenChange,
                    description + " can contain " + chosenChange.getDigit() + " only at " + chosenChange.getCell() + ".");
        } else return null;
        //endregion
    }

    private static List<MaskGroup> groupsWithPairsOfCellsWithSameTwoDigitCandidates(Candidates candidates) {
        var twoDigitGroups =
                candidates.twoDigitMasks().stream()
                        .map(twoDigitMask ->
                                CellGroup.all().stream()
                                        .filter(cellGroup -> cellGroup.stream()
                                                .filter(cell -> candidates.get(cell).getMask().equals(twoDigitMask))
                                                .count() == 2)
                                        .filter(cellGroup -> cellGroup.stream()
                                                .anyMatch(cell -> !candidates.get(cell).getMask().equals(twoDigitMask)
                                                        && (candidates.get(cell).getMask().overlappingWith(twoDigitMask).get()) > 0))
                                        .map(group -> new MaskGroup(twoDigitMask, group))
                                        .collect(toList()))
                        .flatMap(Collection::stream)
                        .collect(toList());
        return twoDigitGroups;
    }


    private static void swap(int[] positions, int pos1, int pos2) {
        int temp = positions[pos1];
        positions[pos1] = positions[pos2];
        positions[pos2] = temp;
    }

    public static void main(String[] args) throws IOException {
        int seed = new Random().nextInt();
        System.out.println("Seed: " + seed);

        play(new Random(seed));

        System.out.println("Seed: " + seed);

        if (System.console() != null) {
            System.out.println();
            System.out.print("Press ENTER to exit... ");
            System.console().readLine();
        }
    }
}

class CandidateChange {
    private final Cell cell;
    private final int digit;

    private CandidateChange(Cell cell, int digit) {
        this.cell = cell;
        this.digit = digit;
    }

    static CandidateChange setDigit(Cell cell, int digit) {
        return new CandidateChange(cell, digit);
    }

    public Cell getCell() {
        return cell;
    }

    public int getDigit() {
        return digit;
    }
}

class Change {
    private final String reason;
    private final Cell cell;
    private final int digit;

    private Change(Cell cell, int digit, String reason) {
        this.reason = reason;
        this.cell = cell;
        this.digit = digit;
    }

    static Change changeWithReason(CandidateChange candidateChange, String reason) {
        return new Change(candidateChange.getCell(), candidateChange.getDigit(), reason);
    }

    public Cell getCell() {
        return cell;
    }

    public int getDigit() {
        return digit;
    }

    public String getReason() {
        return reason;
    }
}

class State extends AbstractList<Integer> {
    private final int[] state;

    State(int[] initialState) {
        this.state = initialState;
    }

    public int[] getState() {
        return state;
    }

    public State copy() {
        int[] copy = new int[state.length];
        System.arraycopy(state, 0, copy, 0, copy.length);
        return new State(copy);
    }

    @Override
    public Integer get(int index) {
        return state[index];
    }

    public Integer get(Cell cell) {
        return state[cell.getIndex()];
    }

    @Override
    public Integer set(int index, Integer value) {
        var prev = state[index];
        state[index] = value;
        return prev;
    }

    public Integer set(Cell cell, Integer value) {
        var prev = state[cell.getIndex()];
        state[cell.getIndex()] = value;
        return prev;
    }

    @Override
    public int size() {
        return state.length;
    }

    boolean hasValue(int i) {
        return state[i] != 0;
    }
}

class Candidates extends AbstractList<Candidate> {
    private static final int allOnes = (1 << 9) - 1;

    private final Map<Integer, Candidate> candidates;

    Candidates(State state) {
        this.candidates = calculateFrom(state);
    }

    List<Mask> twoDigitMasks() {
        return candidates.values().stream()
                .map(candidate -> candidate.getMask())
                .filter(mask -> mask.candidatesCount() == 2)
                .distinct()
                .collect(toList());
    }

    private static Map<Integer, Candidate> calculateFrom(State state) {
        return IntStream.range(0, state.size())
                .mapToObj(i -> {
                    if (state.hasValue(i)) {
                        return new NoCandidate(Cell.of(i));
                    }
                    int collidingDigitsMask =
                            Cell.of(i).allSiblings().stream()
                                    .mapToInt(sibling -> Masks.maskForDigit(state.get(sibling)).get())
                                    .reduce(0, (digitsMask, digitMask) -> digitsMask | digitMask);
                    return new Candidate(Cell.of(i), new Mask(allOnes & ~collidingDigitsMask));
                }).collect(toMap(
                        candidate -> candidate.getCell().getIndex(), Function.identity()
                ));
    }

    @Override
    public Candidate get(int index) {
        return candidates.get(Cell.of(index).getIndex());
    }

    @Override
    public int size() {
        return candidates.size();
    }

    public Candidate get(Cell cell) {
        return candidates.get(cell.getIndex());
    }
}

class NoCandidate extends Candidate {
    NoCandidate(Cell cell) {
        super(cell, new Mask(0));
    }
}

class Candidate {
    private final Cell cell;
    private Mask mask;

    Candidate(Cell cell, Mask mask) {
        this.cell = cell;
        this.mask = mask;
    }

    Integer singleDigit() {
        return getMask().singleDigit();
    }

    boolean matchesMask(Mask other) {
        return getMask().matches(other);
    }

    boolean hasCandidateDigit(int digit) {
        return matchesMask(Masks.maskForDigit(digit));
    }

    void setNoCandidates() {
        mask = new Mask(0);
    }

    public Cell getCell() {
        return cell;
    }

    public Mask getMask() {
        return mask;
    }

    public void setMask(Mask mask) {
        this.mask = mask;
    }

    int candidateDigitsCount() {
        return getMask().candidatesCount();
    }
}

class Mask {
    private static final Map<Integer, Integer> singleBitMaskToDigit = singleBitMaskToDigit();
    static final Map<Integer, Integer> maskToOnesCount = maskToOnesCount();
    private final int mask;

    Mask(int mask) {
        this.mask = mask;
    }

    private static Map<Integer, Integer> maskToOnesCount() {
        Map<Integer, Integer> maskToOnesCount = new HashMap<>();
        maskToOnesCount.put(0, 0);
        for (int i = 1; i < (1 << 9); i++) {
            int smaller = i >> 1;
            int increment = i & 1;
            maskToOnesCount.put(i, maskToOnesCount.get(smaller) + increment);
        }
        return maskToOnesCount;
    }

    Mask minus(Mask other) {
        return overlappingWith(other.inverted());
    }

    Mask overlappingWith(Mask other) {
        return new Mask(get() & other.get());
    }

    Mask inverted() {
        return new Mask(~get());
    }

    Integer singleDigit() {
        return singleBitMaskToDigit.get(get());
    }

    int candidatesCount() {
        return maskToOnesCount.get(get());
    }

    public int get() {
        return mask;
    }

    public List<Integer> digits() {
        return IntStream.range(1, 10)
                .filter(digit -> (mask & (1 << (digit - 1))) != 0)
                .boxed()
                .collect(toUnmodifiableList());
    }

    private static Map<Integer, Integer> singleBitMaskToDigit() {
        Map<Integer, Integer> singleBitMaskToDigit = new HashMap<>();
        for (int i = 0; i < 9; i++)
            singleBitMaskToDigit.put(1 << i, i + 1);

        return singleBitMaskToDigit;
    }

    boolean matches(Mask other) {
        return overlappingWith(other).get() != 0;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        return mask == ((Mask) o).mask;
    }

    @Override
    public int hashCode() {
        return mask;
    }
}

class Masks {
    static final List<Mask> nMasks = nMasks();

    static Mask maskForDigit(int digit) {
        return new Mask(1 << (digit - 1));
    }

    // masks that represent two or more candidates
    private static List<Mask> nMasks() {
        return Mask.maskToOnesCount.entrySet().stream()
                .filter(tuple -> tuple.getValue() > 1)
                .map(tuple -> new Mask(tuple.getKey()))
                .collect(toList());
    }
}

class Board {
    private final State state;

    Board(State state) {
        this.state = state;
    }

    private static List<String> board(int[] state) {
        char[][] board = emptyBoard();
        for (int i = 0; i < state.length; i++) {
            int tempRow = i / 9;
            int tempCol = i % 9;
            int rowToWrite = tempRow + tempRow / 3 + 1;
            int colToWrite = tempCol + tempCol / 3 + 1;

            board[rowToWrite][colToWrite] = '.';
            if (state[i] > 0)
                board[rowToWrite][colToWrite] = (char) ('0' + state[i]);
        }

        return Arrays.stream(board).map(it -> new String(it)).collect(toList());
    }

    // Prepare empty board
    private static char[][] emptyBoard() {
        String line = "+---+---+---+";
        String middle = "|...|...|...|";
        return new char[][]
                {
                        line.toCharArray(),
                        middle.toCharArray(),
                        middle.toCharArray(),
                        middle.toCharArray(),
                        line.toCharArray(),
                        middle.toCharArray(),
                        middle.toCharArray(),
                        middle.toCharArray(),
                        line.toCharArray(),
                        middle.toCharArray(),
                        middle.toCharArray(),
                        middle.toCharArray(),
                        line.toCharArray()
                };
    }

    void printCode() {
        String code = Arrays.stream(state.getState()).mapToObj(Integer::toString).collect(Collectors.joining(""));
        System.out.format("Code: %s", code).println();
    }

    void printBoard() {
        System.out.println(String.join(System.lineSeparator(), board(state.getState())));
    }
}

class MaskGroup extends AbstractList<Cell> {
    private final Mask mask;
    private final CellGroup group;

    public MaskGroup(Mask mask, CellGroup group) {
        this.mask = mask;
        this.group = group;
    }

    public Mask getMask() {
        return mask;
    }

    public String getDescription() {
        return group.getDescription();
    }

    @Override
    public Cell get(int index) {
        return group.get(index);
    }

    @Override
    public int size() {
        return group.size();
    }

    public List<Cell> cellsWithMask(State state, Candidates candidates, Mask mask) {
        return group.cellsWithMask(state, candidates, mask);
    }
}

class CellGroup extends AbstractList<Cell> {
    private static final List<CellGroup> rows = rowCellGroups();
    private static final List<CellGroup> columns = columnCellGroups();
    private static final List<CellGroup> blocks = blockCellGroups();

    private static final List<CellGroup> all = buildCellGroups();

    private final String description;
    private final List<Cell> cells;

    private CellGroup(String description, List<Cell> cells) {
        this.description = description;
        this.cells = Collections.unmodifiableList(cells);
    }

    public static List<CellGroup> rows() {
        return rows;
    }

    public static List<CellGroup> columns() {
        return columns;
    }

    public static List<CellGroup> blocks() {
        return blocks;
    }

    static List<CellGroup> all() {
        return all;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public Cell get(int index) {
        return cells.get(index);
    }

    @Override
    public int size() {
        return cells.size();
    }

    private static List<CellGroup> buildCellGroups() {
        //region Build a collection (named cellGroups) which maps cell indices into distinct groups (rows/columns/blocks)
        return Collections.unmodifiableList(Stream.of(rows, columns, blocks)
                .flatMap(Collection::stream)
                .collect(toList()));
        //endregion
    }

    private static List<CellGroup> rowCellGroups() {
        var rowCellGroups =
                Cell.cells().stream()
                        .collect(groupingBy(Cell::getRow))
                        .entrySet().stream()
                        .map(group -> new CellGroup(
                                "row #" + (group.getKey() + 1),
                                group.getValue()))
                        .collect(toList());
        return rowCellGroups;
    }

    private static List<CellGroup> columnCellGroups() {
        var columnCellGroups =
                Cell.cells().stream()
                        .collect(groupingBy(Cell::getColumn))
                        .entrySet().stream()
                        .map(group -> new CellGroup(
                                "column #" + (group.getKey() + 1),
                                group.getValue()))
                        .collect(toList());
        return columnCellGroups;
    }

    private static List<CellGroup> blockCellGroups() {
        return Cell.cells().stream()
                .collect(groupingBy(Cell::getBlock))
                .entrySet().stream()
                .map(group -> new CellGroup(
                        format("block (%s, %s)", group.getKey() / 3 + 1, group.getKey() % 3 + 1),
                        group.getValue()))
                .collect(toList());
    }

    public List<Cell> cellsWithMask(State state, Candidates candidates, Mask mask) {
        return this.stream()
                .filter(cell -> state.get(cell) == 0 && (candidates.get(cell).getMask().matches(mask)))
                .collect(toList());
    }

}

class Cell {
    private static final Cell[] cells = new Cell[9 * 9];

    static {
        for (int index = 0; index < 9 * 9; index++)
            cells[index] = new Cell(index);
    }

    static List<Cell> cells() {
        return List.of(cells);
    }

    private final int index;

    private Cell(int index) {
        this.index = index;
    }

    static Cell of(int index) {
        return cells[index];
    }

    static Cell of(int row, int col) {
        return Cell.of(9 * row + col);
    }

    static Cell ofBlock(int block, int indexInBlock) {
        int blockRowIndex = (block / 3) * 3 + indexInBlock / 3;
        int blockColIndex = (block % 3) * 3 + indexInBlock % 3;
        int index = blockRowIndex * 9 + blockColIndex;
        return Cell.of(index);
    }

    static boolean sharesACellGroup(Cell cell1, Cell cell2) {
        return cell1.getRow() == cell2.getRow()
                || cell1.getColumn() == cell2.getColumn()
                || cell1.getBlock() == cell2.getBlock();
    }

    int getIndex() {
        return index;
    }

    int getRow() {
        return index / 9;
    }

    int getColumn() {
        return index % 9;
    }

    int getBlockCol() {
        return getColumn() / 3;
    }

    int getBlockRow() {
        return getRow() / 3;
    }

    int getBlock() {
        return 3 * getBlockRow() + getBlockCol();
    }

    Cell rowSibling(int i) {
        return of(rowSiblingIndex(i));
    }

    private int rowSiblingIndex(int i) {
        return 9 * getRow() + i;
    }

    Cell columnSibling(int i) {
        return of(columnSiblingIndex(i));
    }

    private int columnSiblingIndex(int i) {
        return 9 * i + getColumn();
    }

    Cell blockSibling(int i) {
        return of(blockSiblingIndex(i));
    }

    private int blockSiblingIndex(int i) {
        return 9 * (getBlockRow() * 3 + i / 3) + getBlockCol() * 3 + i % 3;
    }

    List<Cell> rowSiblings() {
        List<Cell> siblings = new ArrayList<>();
        for (int j = 0; j < 9; j++) {
            siblings.add(rowSibling(j));
        }
        return siblings;
    }

    List<Cell> columnSiblings() {
        List<Cell> siblings = new ArrayList<>();
        for (int j = 0; j < 9; j++) {
            siblings.add(columnSibling(j));
        }
        return siblings;
    }

    List<Cell> blockSiblings() {
        List<Cell> siblings = new ArrayList<>();
        for (int j = 0; j < 9; j++) {
            siblings.add(blockSibling(j));
        }
        return siblings;
    }

    List<Cell> allSiblings() {
        List<Cell> allSiblings = new ArrayList<>();
        allSiblings.addAll(rowSiblings());
        allSiblings.addAll(columnSiblings());
        allSiblings.addAll(blockSiblings());
        return allSiblings;
    }

    @Override
    public String toString() {
        return format("(%s, %s)", getRow() + 1, getColumn() + 1);
    }
}