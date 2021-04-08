package sudoku.kata;

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
                    List<DigitsSetGroup> twoDigitGroups = groupsWithPairsOfCellsWithSameTwoDigitCandidates(candidates);

                    if (!twoDigitGroups.isEmpty()) {
                        for (var twoDigitGroup : twoDigitGroups) {
                            var cellsToCleanUp =
                                    twoDigitGroup.stream()
                                            .filter(cell -> !candidates.get(cell).getDigits().equals(twoDigitGroup.getDigits())
                                                    && (candidates.get(cell).getDigits().overlappingWith(twoDigitGroup.getDigits()).size()) > 0)
                                            .collect(toList());

                            if (!cellsToCleanUp.isEmpty()) {
                                var maskCells =
                                        twoDigitGroup.stream()
                                                .filter(cell ->
                                                        candidates.get(cell).getDigits().equals(twoDigitGroup.getDigits()))
                                                .collect(toList());

                                List<Integer> digitsInGroup = twoDigitGroup.getDigits();
                                System.out.println(
                                        "Values " + digitsInGroup.get(0) + " and " + digitsInGroup.get(1) + " in " + twoDigitGroup.getDescription() +
                                                " are in cells " + maskCells.get(0) +
                                                " and " + maskCells.get(1) + ".");

                                for (var cell : cellsToCleanUp) {
                                    Candidate candidate = candidates.get(cell);
                                    DigitsSet digitsToRemove = candidate.getDigits().overlappingWith(twoDigitGroup.getDigits());

                                    String valuesReport = digitsToRemove.stream().map(Object::toString).collect(joining(", "));
                                    System.out.println(valuesReport + " cannot appear in " + cell + ".");

                                    candidate.setDigits(candidate.getDigits().minus(twoDigitGroup.getDigits()));
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

                    var groupsWithNDigitsSets =
                            DigitsSet.nDigitsSet.stream()
                                    .map(digitsSet -> CellGroup.all().stream()
                                            .filter(group -> group.stream().allMatch(cell -> state.get(cell) == 0 || !digitsSet.matches(new DigitsSet(List.of(state.get(cell))))))
                                            .map(group -> new DigitsSetGroup(digitsSet, group))
                                            .filter(group -> group.candidatesWithDigitsSet(state, candidates, digitsSet).size() == group.getDigits().size())
                                            .collect(toList()))
                                    .flatMap(Collection::stream)
                                    .collect(toList());


                    for (var groupWithNDigitsSets : groupsWithNDigitsSets) {
                        DigitsSet nDigitsSet = groupWithNDigitsSets.getDigits();

                        if (groupWithNDigitsSets.stream()
                                .anyMatch(cell ->
                                        candidates.get(cell).matchesDigitsSet(nDigitsSet)
                                                && candidates.get(cell).matchesDigitsSet(nDigitsSet.inverted()))) {

                            StringBuilder message = new StringBuilder();
                            message.append("In " + groupWithNDigitsSets.getDescription() + " values ");
                            message.append(nDigitsSet.stream().map(Object::toString).collect(joining(", ")));
                            message.append(" appear only in cells");
                            groupWithNDigitsSets
                                    .candidatesWithDigitsSet(state, candidates, nDigitsSet)
                                    .stream().map(candidate -> " " + candidate)
                                    .forEach(message::append);
                            message.append(" and other values cannot appear in those cells.");

                            System.out.println(message.toString());
                        }

                        for (var candidate : groupWithNDigitsSets.candidatesWithDigitsSet(state, candidates, nDigitsSet)) {
                            DigitsSet digitsToClear = candidate.getDigits().minus(nDigitsSet);
                            if (digitsToClear.size() == 0)
                                continue;

                            String message =
                                    digitsToClear.stream()
                                            .map(Object::toString)
                                            .collect(joining(", ")) +
                                            " cannot appear in cell " + (Cell) candidate + ".";
                            System.out.println(message);

                            candidate.setDigits(candidate.getDigits().overlappingWith(nDigitsSet));
                            stepChangeMade = true;
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

                        var digits = (List<Integer>) candidateI.getDigits();

                        for (int j = candidates.indexOf(candidateI) + 1; j < candidates.size(); j++) {
                            Candidate candidateJ = candidates.get(j);
                            if (((List<Integer>) candidateJ.getDigits()).equals(candidateI.getDigits())) {
                                if (Cell.sharesACellGroup(candidateI, candidateJ)) {
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

                    if (solutionState.get(candidate1) == digit1) {
                        alternateState.set(candidate1, digit2);
                        alternateState.set(candidate2, digit1);
                    } else {
                        alternateState.set(candidate1, digit1);
                        alternateState.set(candidate2, digit2);
                    }

                    {
                        // What follows below is a complete copy-paste of the solver which appears at the beginning of this method
                        // However, the algorithm couldn't be applied directly and it had to be modified.
                        // Implementation below assumes that the board might not have a solution.

                        Stack<State> stateStack = new Stack<>();
                        Stack<Cell> cellStack = new Stack<>();
                        Stack<boolean[]> usedDigitsStack = new Stack<>();
                        Stack<Integer> lastDigitStack = new Stack<>();

                        Command command = Command.EXPAND;

                        while (!command.equals(Command.COMPLETE) && !command.equals(Command.FAIL)) {
                            if (command.equals(Command.EXPAND)) {
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

                                        DigitsSet digitsUsedSet = new DigitsSet(digitsUsed(currentState, cell.allSiblings()));

                                        boolean[] isDigitUsed = new boolean[9];

                                        digitsUsedSet.forEach(digit ->
                                                isDigitUsed[digit - 1] = true
                                        );

                                        int candidatesCount = digitsUsedSet.inverted().size();

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
                                command = Command.MOVE;

                            } // if (command == "expand")
                            else if (command.equals(Command.COLLAPSE)) {
                                stateStack.pop();
                                cellStack.pop();
                                usedDigitsStack.pop();
                                lastDigitStack.pop();

                                if (!stateStack.empty())
                                    command = Command.MOVE; // Always try to move after collapse
                                else
                                    command = Command.FAIL;
                            } else if (command.equals(Command.MOVE)) {

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

                                    if (currentState.stream().anyMatch(cellState -> cellState.getState() == 0))
                                        command = Command.EXPAND;
                                    else
                                        command = Command.COMPLETE;
                                } else {
                                    // No viable candidate was found at current position - pop it in the next iteration
                                    lastDigitStack.push(0);
                                    command = Command.COLLAPSE;
                                }
                            } // if (command == "move")

                        } // while (command != "complete" && command != "fail")

                        if (command.equals(Command.COMPLETE)) {   // Board was solved successfully even with two digits swapped
                            cellList1.add(candidate1);
                            cellList2.add(candidate2);
                            digitList1.add(digit1);
                            digitList2.add(digit2);
                        }
                    }
                } // while (!candidateIndex1.empty())

                if (!cellList1.isEmpty()) {
                    int pos = rng.nextInt(cellList1.size());

                    Cell cell1 = cellList1.get(pos);
                    Cell cell2 = cellList2.get(pos);

                    String description;

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

    static Set<Integer> digitsUsed(State currentState, List<Cell> cells) {
        return cells.stream()
                .map(sibling -> currentState.get(sibling))
                .filter(i -> i != 0)
                .collect(Collectors.toSet());
    }

    private static void printSolutionState(State solutionState) {
        System.out.println();
        System.out.println("Final look of the solved board:");
        StatePrinter.printBoard(solutionState);
    }

    private static void printStartingState(State startingState) {
        System.out.println();
        System.out.println("Starting look of the board to solve:");
        StatePrinter.printBoard(startingState);
    }

    private static void printDivider() {
        System.out.println();
        System.out.println("=".repeat(80));
        System.out.println();
    }

    private static void printState(State state) {
        StatePrinter.printBoard(state);
        StatePrinter.printCode(state);
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
            startingState.set(Cell.of(positions[i]), 0);
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
        Command command = Command.EXPAND;
        while (stateStack.size() <= 9 * 9) {
            if (command.equals(Command.EXPAND)) {
                final State currentState;

                if (stateStack.isEmpty()) {
                    currentState = new State(new int[9 * 9]);
                } else {
                    currentState = stateStack.peek().copy();
                }

                Cell bestCell = null;
                boolean[] bestUsedDigits = null;
                int bestCandidatesCount = -1;
                int bestRandomValue = -1;
                boolean containsUnsolvableCells = false;

                for (var cell : Cell.cells()) {
                    if (currentState.get(cell) == 0) {

                        DigitsSet digitsUsedSet = new DigitsSet(digitsUsed(currentState, cell.allSiblings()));

                        boolean[] isDigitUsed = new boolean[9];

                        digitsUsedSet.forEach(digit ->
                                isDigitUsed[digit - 1] = true
                        );

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
                command = Command.MOVE;

            } // if (command == "expand")
            else if (command.equals(Command.COLLAPSE)) {
                stateStack.pop();
                cellStack.pop();
                usedDigitsStack.pop();
                lastDigitStack.pop();

                command = Command.MOVE;   // Always try to move after collapse
            } else if (command.equals(Command.MOVE)) {

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
                    command = Command.EXPAND;
                } else {
                    // No viable candidate was found at current position - pop it in the next iteration
                    lastDigitStack.push(0);
                    command = Command.COLLAPSE;
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

        String reason = String.format("%s can only contain %s.", (Cell) singleCandidate, digit);

        return Change.changeWithReason(new CandidateChange(singleCandidate, digit), reason);
    }

    private static Change pickACellInAGroupThatOnlyCanHaveADigitInOnePlace(Random rng, Candidates candidates) {
        //region Try to find a number which can only appear in one place in a row/column/block
        List<String> groupDescriptions = new ArrayList<>();
        List<CandidateChange> candidateChanges = new ArrayList<>();

        for (int digit = 1; digit <= 9; digit++) {
            for (int cellGroupIndex = 0; cellGroupIndex < 9; cellGroupIndex++) {

                int rowNumberCount = 0;
                Cell rowCandidate = null;
                for (Cell cell : CellGroup.rows().get(cellGroupIndex)) {
                    if (candidates.get(cell).hasCandidateDigit(digit)) {
                        rowNumberCount += 1;
                        rowCandidate = cell;
                    }
                }

                if (rowNumberCount == 1) {
                    groupDescriptions.add("Row #" + (cellGroupIndex + 1));
                    candidateChanges.add(new CandidateChange(rowCandidate, digit));
                }

                int colNumberCount = 0;
                Cell colCandidate = null;
                for (Cell cell : CellGroup.columns().get(cellGroupIndex)) {
                    if (candidates.get(cell).hasCandidateDigit(digit)) {
                        colNumberCount += 1;
                        colCandidate = cell;
                    }
                }

                if (colNumberCount == 1) {
                    groupDescriptions.add("Column #" + (cellGroupIndex + 1));
                    candidateChanges.add(new CandidateChange(colCandidate, digit));
                }

                int blockNumberCount = 0;
                Cell blockCandidate = null;
                for (Cell cell : CellGroup.blocks().get(cellGroupIndex)) {
                    if (candidates.get(cell).hasCandidateDigit(digit)) {
                        blockNumberCount += 1;
                        blockCandidate = cell;
                    }
                }

                if (blockNumberCount == 1) {
                    int blockRow = cellGroupIndex / 3;
                    int blockCol = cellGroupIndex % 3;

                    groupDescriptions.add("Block (" + (blockRow + 1) + ", " + (blockCol + 1) + ")");
                    candidateChanges.add(new CandidateChange(blockCandidate, digit));
                }
            } // for (cellGroup = 0..8)
        } // for (digit = 1..9)

        if (candidateChanges.size() > 0) {
            int index = rng.nextInt(candidateChanges.size());
            String description = groupDescriptions.get(index);
            CandidateChange chosenChange = candidateChanges.get(index);

            return Change.changeWithReason(chosenChange,
                    description + " can contain " + chosenChange.getDigit() + " only at " + chosenChange.getCell() + ".");
        } else return null;
        //endregion
    }

    private static List<DigitsSetGroup> groupsWithPairsOfCellsWithSameTwoDigitCandidates(Candidates candidates) {
        var twoDigitGroups =
                candidates.twoDigitDigitsSets().stream()
                        .map(twoDigitDigitsSet ->
                                CellGroup.all().stream()
                                        .filter(cellGroup -> cellGroup.stream()
                                                .filter(cell -> candidates.get(cell).getDigits().equals(twoDigitDigitsSet))
                                                .count() == 2)
                                        .map(group -> new DigitsSetGroup(twoDigitDigitsSet, group))
                                        .collect(toList()))
                        .flatMap(Collection::stream)
                        .collect(toList());
        var twoDigitGroupsWithCellsThatCanBeCleaned =
                twoDigitGroups.stream()
                        .filter(digitsSetGroup -> digitsSetGroup.stream()
                                .anyMatch(cell -> !candidates.get(cell).getDigits().equals(digitsSetGroup.getDigits())
                                        && (candidates.get(cell).getDigits().overlappingWith(digitsSetGroup.getDigits()).size()) > 0))
                        .collect(toList());
        return twoDigitGroupsWithCellsThatCanBeCleaned;
    }


    private static void swap(int[] positions, int pos1, int pos2) {
        int temp = positions[pos1];
        positions[pos1] = positions[pos2];
        positions[pos2] = temp;
    }

    public static void main(String[] args) {
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

    CandidateChange(Cell cell, int digit) {
        this.cell = cell;
        this.digit = digit;
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

class State extends AbstractList<State.CellState> {
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
    public CellState get(int index) {
        return new CellState(index);
    }

    public Integer get(Cell cell) {
        return state[cell.getIndex()];
    }

    @Override
    public CellState set(int index, CellState value) {
        var prev = get(index).frozen();
        state[index] = value.getState();
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

    class CellState {
        private final int stateIndex;

        CellState(int stateIndex) {
            this.stateIndex = stateIndex;
        }

        Cell getCell() {
            return Cell.of(stateIndex);
        }

        int getState() {
            return State.this.getState()[stateIndex];
        }

        FrozenCellState frozen() {
            return new FrozenCellState(stateIndex);
        }

    }

    class FrozenCellState extends State.CellState {
        private final int value;

        FrozenCellState(int stateIndex) {
            super(stateIndex);
            this.value = State.this.get(getCell());
        }

        @Override
        int getState() {
            return value;
        }

    }
}

class Candidates extends AbstractList<Candidate> {

    private final Map<Integer, Candidate> candidates;

    Candidates(State state) {
        this.candidates = calculateFrom(state);
    }

    List<DigitsSet> twoDigitDigitsSets() {
        return candidates.values().stream()
                .map(Candidate::getDigits)
                .filter(digitsSet -> digitsSet.size() == 2)
                .distinct()
                .collect(toList());
    }

    private static Map<Integer, Candidate> calculateFrom(State state) {
        return IntStream.range(0, state.size())
                .mapToObj(i -> {
                    Cell cell = Cell.of(i);
                    if (state.hasValue(i)) {
                        return new NoCandidate(cell);
                    }
                    Set<Integer> digitsUsed = Program.digitsUsed(state, cell.allSiblings());
                    DigitsSet candidateDigitsSet = new DigitsSet(digitsUsed).inverted();
                    return new Candidate(cell, candidateDigitsSet);
                }).collect(toMap(
                        candidate -> candidate.getIndex(), Function.identity()
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
        super(cell, new DigitsSet(List.of()));
    }
}

class Candidate extends Cell {
    private DigitsSet digits;

    Candidate(Cell cell, DigitsSet digits) {
        super(cell.getIndex());
        this.digits = digits;
    }

    Integer singleDigit() {
        return digits.singleDigit();
    }

    boolean matchesDigitsSet(DigitsSet other) {
        return digits.matches(other);
    }

    boolean hasCandidateDigit(int digit) {
        return matchesDigitsSet(new DigitsSet(List.of(digit)));
    }

    void setNoCandidates() {
        digits = new DigitsSet(List.of());
    }

    public DigitsSet getDigits() {
        return digits;
    }

    public void setDigits(DigitsSet digits) {
        this.digits = digits;
    }

    int candidateDigitsCount() {
        return digits.size();
    }
}

class DigitsSet extends AbstractList<Integer> {
    static final List<DigitsSet> nDigitsSet = nDigitDigitsSets();
    private static final DigitsSet allDigits = new DigitsSet(Set.of(1, 2, 3, 4, 5, 6, 7, 8, 9));
    private final List<Integer> digits;

    private DigitsSet(int mask) {
        this.digits = IntStream.range(1, 10).filter(i -> (mask & (1 << (i - 1))) != 0).boxed().sorted().collect(toList());;
    }

    public DigitsSet(Collection<Integer> possibleDigits) {
        this.digits = possibleDigits.stream().sorted().collect(toList());
    }

    static DigitsSet digitsSetFromIntMask(int i) {
        return new DigitsSet(i);
    }

    // digitSets that represent two or more candidates
    private static List<DigitsSet> nDigitDigitsSets() {
        return IntStream.range(1, (1 << 9))
                .mapToObj(i -> digitsSetFromIntMask(i))
                .filter(digitsSet -> digitsSet.size() > 1)
                .collect(toList());
    }

    DigitsSet inverted() {
        return new DigitsSet(allDigits.stream()
                .filter(digit -> !digits.contains(digit))
                .collect(Collectors.toSet()));
    }

    DigitsSet minus(DigitsSet other) {
        return overlappingWith(other.inverted());
    }

    DigitsSet overlappingWith(DigitsSet other) {
        return new DigitsSet(this.stream().filter(other::contains).collect(toSet()));
    }

    Integer singleDigit() {
        return this.get(0);
    }

    boolean matches(DigitsSet other) {
        return !overlappingWith(other).isEmpty();
    }

    @Override
    public Integer get(int index) {
        return digits.get(index);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (digits.size() != ((DigitsSet) o).digits.size()) return false;
        return digits.containsAll(((DigitsSet) o).digits);
    }

    @Override
    public int hashCode() {
        return digits.hashCode();
    }

    @Override
    public int size() {
        return digits.size();
    }
}

class StatePrinter {

    static void printBoard(State state) {
        boardLines(state).forEach(System.out::println);
    }

    private static List<String> boardLines(State state) {
        char[][] board = emptyBoard();
        for (State.CellState cellState : state) {
            Cell cell = cellState.getCell();
            int rowToWrite = cell.getRow() + cell.getRow() / 3 + 1;
            int colToWrite = cell.getColumn() + cell.getColumn() / 3 + 1;

            board[rowToWrite][colToWrite] = cellState.getState() <= 0 ? '.' : (char) ('0' + cellState.getState());
        }

        return Arrays.stream(board).map(String::new).collect(toList());
    }

    static void printCode(State state) {
        String code = state.stream().map(State.CellState::getState).map(i -> Integer.toString(i)).collect(Collectors.joining(""));
        System.out.format("Code: %s", code).println();
    }

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
}

class DigitsSetGroup extends AbstractList<Cell> {
    private final DigitsSet digits;
    private final CellGroup group;

    public DigitsSetGroup(DigitsSet digits, CellGroup group) {
        this.digits = digits;
        this.group = group;
    }

    public DigitsSet getDigits() {
        return digits;
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

    public List<Candidate> candidatesWithDigitsSet(State state, Candidates candidates, DigitsSet digitsSet) {
        return group.candidatesWithDigitsSet(state, candidates, digitsSet);
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

    // Builds a collection (named cellGroups) which maps cell indices into distinct groups (rows/columns/blocks)
    private static List<CellGroup> buildCellGroups() {
        return Collections.unmodifiableList(Stream.of(rows, columns, blocks)
                .flatMap(Collection::stream)
                .collect(toList()));
    }

    private static List<CellGroup> rowCellGroups() {
        return Cell.cells().stream()
                .collect(groupingBy(Cell::getRow))
                .entrySet().stream()
                .map(group -> new CellGroup(
                        "row #" + (group.getKey() + 1),
                        group.getValue()))
                .collect(toList());
    }

    private static List<CellGroup> columnCellGroups() {
        return Cell.cells().stream()
                .collect(groupingBy(Cell::getColumn))
                .entrySet().stream()
                .map(group -> new CellGroup(
                        "column #" + (group.getKey() + 1),
                        group.getValue()))
                .collect(toList());
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

    public List<Candidate> candidatesWithDigitsSet(State state, Candidates candidates, DigitsSet digitsSet) {
        return this.stream()
                .filter(cell -> state.get(cell) == 0 && (candidates.get(cell).matchesDigitsSet(digitsSet)))
                .map(candidates::get)
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

    protected Cell(int index) {
        this.index = index;
    }

    static Cell of(int index) {
        return cells[index];
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

    private CellGroup rowSiblings() {
        return CellGroup.rows().get(getRow());
    }

    private CellGroup columnSiblings() {
        return CellGroup.columns().get(getColumn());
    }

    private CellGroup blockSiblings() {
        return CellGroup.blocks().get(getBlock());
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

enum Command {
    EXPAND,
    COMPLETE,
    FAIL,
    MOVE,
    COLLAPSE
}