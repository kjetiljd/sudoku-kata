package sudoku.kata;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

public class Program {

    static void play() {
        play(new Random());
    }

    static void play(Random rng) {

        State solutionState = constructBoardToBeSolved(rng);

        System.out.println();
        System.out.println("Final look of the solved board:");
        new Board(solutionState).printBoard();

        State startingState = generateStartingState(rng, solutionState);

        System.out.println();
        System.out.println("Starting look of the board to solve:");
        new Board(startingState).printBoard();

        System.out.println();
        System.out.println("=".repeat(80));
        System.out.println();

        State state = startingState.copy();

        boolean changeMade = true;
        while (changeMade) {
            changeMade = false;

            Candidates candidates = new Candidates(state);

            boolean stepChangeMade = true;
            while (stepChangeMade) {
                stepChangeMade = false;

                //region Pick cells with only one candidate left

                List<Candidate> singleCandidates = candidates.singleCandidates();

                if (!singleCandidates.isEmpty()) {
                    Candidate singleCandidate = singleCandidates.get(rng.nextInt(singleCandidates.size()));
                    int digit = singleCandidate.getMask().singleDigit();

                    System.out.format("%s can only contain %s.", singleCandidate.getCell(), digit).println();

                    state.set(singleCandidate.getCell(), digit);
                    singleCandidate.setNoCandidates();
                    changeMade = true;
                }

                //endregion

                //region Try to find a number which can only appear in one place in a row/column/block

                if (!changeMade) {
                    List<String> groupDescriptions = new ArrayList<>();
                    List<Integer> candidateRowIndices = new ArrayList<>();
                    List<Integer> candidateColIndices = new ArrayList<>();
                    List<Integer> candidateList = new ArrayList<>();

                    for (int digit = 1; digit <= 9; digit++) {
                        int mask = Masks.maskForDigit(digit);
                        for (int cellGroup = 0; cellGroup < 9; cellGroup++) {
                            int rowNumberCount = 0;
                            int indexInRow = 0;

                            int colNumberCount = 0;
                            int indexInCol = 0;

                            int blockNumberCount = 0;
                            int indexInBlock = 0;

                            for (int indexInGroup = 0; indexInGroup < 9; indexInGroup++) {
                                int rowStateIndex = 9 * cellGroup + indexInGroup;
                                int colStateIndex = 9 * indexInGroup + cellGroup;
                                int blockRowIndex = (cellGroup / 3) * 3 + indexInGroup / 3;
                                int blockColIndex = (cellGroup % 3) * 3 + indexInGroup % 3;
                                int blockStateIndex = blockRowIndex * 9 + blockColIndex;

                                if ((candidates.get(rowStateIndex).getMask().get() & mask) != 0) {
                                    rowNumberCount += 1;
                                    indexInRow = indexInGroup;
                                }

                                if ((candidates.get(colStateIndex).getMask().get() & mask) != 0) {
                                    colNumberCount += 1;
                                    indexInCol = indexInGroup;
                                }

                                if ((candidates.get(blockStateIndex).getMask().get() & mask) != 0) {
                                    blockNumberCount += 1;
                                    indexInBlock = indexInGroup;
                                }
                            }

                            if (rowNumberCount == 1) {
                                groupDescriptions.add("Row #" + (cellGroup + 1));
                                candidateRowIndices.add(cellGroup);
                                candidateColIndices.add(indexInRow);
                                candidateList.add(digit);
                            }

                            if (colNumberCount == 1) {
                                groupDescriptions.add("Column #" + (cellGroup + 1));
                                candidateRowIndices.add(indexInCol);
                                candidateColIndices.add(cellGroup);
                                candidateList.add(digit);
                            }

                            if (blockNumberCount == 1) {
                                int blockRow = cellGroup / 3;
                                int blockCol = cellGroup % 3;

                                groupDescriptions.add("Block (" + (blockRow + 1) + ", " + (blockCol + 1) + ")");
                                candidateRowIndices.add(blockRow * 3 + indexInBlock / 3);
                                candidateColIndices.add(blockCol * 3 + indexInBlock % 3);
                                candidateList.add(digit);
                            }
                        } // for (cellGroup = 0..8)
                    } // for (digit = 1..9)

                    if (candidateList.size() > 0) {
                        int index = rng.nextInt(candidateList.size());
                        String description = groupDescriptions.get(index);
                        int row = candidateRowIndices.get(index);
                        int col = candidateColIndices.get(index);
                        int digit = candidateList.get(index);

                        String message = description + " can contain " + digit + " only at (" + (row + 1) + ", " + (col + 1) + ").";

                        int stateIndex = 9 * row + col;
                        state.set(stateIndex, digit);
                        candidates.get(stateIndex).setNoCandidates();
                        changeMade = true;

                        System.out.println(message);
                    }
                }

                //endregion

                //region Try to find pairs of digits in the same row/column/block and remove them from other colliding cells
                if (!changeMade) {

                    var twoDigitGroups =
                            candidates.twoDigitMasks().stream()
                                    .map(twoDigitMask ->
                                            CellGroup.all().stream()
                                                    .filter(group -> group.stream()
                                                            .filter(cell -> candidates.get(cell).getMask().get() == twoDigitMask.get())
                                                            .count() == 2)
                                                    .filter(group -> group.stream()
                                                            .anyMatch(cell -> candidates.get(cell).getMask().get() != twoDigitMask.get()
                                                                    && (candidates.get(cell).getMask().get() & twoDigitMask.get()) > 0))
                                                    .map(group -> new MaskGroup(twoDigitMask.get(), group))
                                                    .collect(toList()))
                                    .flatMap(Collection::stream)
                                    .collect(toList());

                    if (!twoDigitGroups.isEmpty()) {
                        for (var twoDigitGroup : twoDigitGroups) {
                            var cells =
                                    twoDigitGroup.getGroup().stream()
                                            .filter(cell ->
                                                    candidates.get(cell).getMask().get() != twoDigitGroup.getMask()
                                                            && (candidates.get(cell).getMask().get() & twoDigitGroup.getMask()) > 0)
                                            .collect(toList());

                            var maskCells =
                                    twoDigitGroup.getGroup().stream()
                                            .filter(cell ->
                                                    candidates.get(cell).getMask().get() == twoDigitGroup.getMask())
                                            .collect(toList());

                            if (!cells.isEmpty()) {
                                int upper = 0;
                                int lower = 0;
                                int temp = twoDigitGroup.getMask();

                                int value = 1;
                                while (temp > 0) {
                                    if ((temp & 1) > 0) {
                                        lower = upper;
                                        upper = value;
                                    }
                                    temp = temp >> 1;
                                    value += 1;
                                }

                                System.out.println(
                                        "Values " + lower + " and " + upper + " in " + twoDigitGroup.getGroup().getDescription() +
                                                " are in cells " + maskCells.get(0) +
                                                " and " + maskCells.get(1) + ".");

                                for (var cell : cells) {
                                    int maskToRemove = candidates.get(cell).getMask().get() & twoDigitGroup.getMask();
                                    List<Integer> valuesToRemove = new ArrayList<>();
                                    int curValue = 1;
                                    while (maskToRemove > 0) {
                                        if ((maskToRemove & 1) > 0) {
                                            valuesToRemove.add(curValue);
                                        }
                                        maskToRemove = maskToRemove >> 1;
                                        curValue += 1;
                                    }

                                    String valuesReport = String.join(", ", valuesToRemove.stream().map(Object::toString).collect(Collectors.toList()));
                                    System.out.println(valuesReport + " cannot appear in " + cell + ".");

                                    candidates.get(cell).setMask(new Mask(candidates.get(cell).getMask().get() & ~(int) twoDigitGroup.getMask()));
                                    stepChangeMade = true;
                                }
                            }
                        }
                    }
                }
                //endregion

                //region Try to find groups of digits of size N which only appear in N cells within row/column/block
                // When a set of N digits only appears in N cells within row/column/block, then no other digit can appear in the same set of cells
                // All other candidates can then be removed from those cells

                if (!changeMade && !stepChangeMade) {

                    var groupsWithNMasks =
                            Masks.nMasks.stream()
                                    .map(mask -> CellGroup.all().stream()
                                            .filter(group -> group.stream().allMatch(cell ->
                                                    state.get(cell) == 0 || (mask & (Masks.maskForDigit(state.get(cell)))) == 0))
                                            .map(group -> Map.of(
                                                    "Mask", mask,
                                                    "Cells", group,
                                                    "CleanableCellsCount",
                                                    (int) group.stream()
                                                            .filter(cell -> (state.get(cell) == 0) &&
                                                                    ((candidates.get(cell).getMask().get() & mask) != 0) &&
                                                                    ((candidates.get(cell).getMask().get() & ~mask) != 0))
                                                            .count()))
                                            .filter(group -> ((CellGroup) (group.get("Cells"))).cellsWithMask(mask, state, candidates).size() == new Mask((Integer) group.get("Mask")).candidatesCount())
                                            .collect(toList()))
                                    .flatMap(Collection::stream)
                                    .collect(toList());


                    for (var groupWithNMasks : groupsWithNMasks) {
                        int mask = (int) groupWithNMasks.get("Mask");

                        if (((CellGroup) groupWithNMasks.get("Cells")).stream()
                                .anyMatch(cell ->
                                        ((candidates.get(cell).getMask().get() & mask) != 0) &&
                                                ((candidates.get(cell).getMask().get() & ~mask) != 0))) {
                            StringBuilder message = new StringBuilder();
                            message.append("In " + ((CellGroup) groupWithNMasks.get("Cells")).getDescription() + " values ");

                            String separator = "";
                            int temp = mask;
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
                            for (var cell : ((CellGroup) groupWithNMasks.get("Cells")).cellsWithMask(mask, state, candidates)) {
                                message.append(" " + cell);
                            }

                            message.append(" and other values cannot appear in those cells.");

                            System.out.println(message.toString());
                        }

                        for (var cell : ((CellGroup) groupWithNMasks.get("Cells")).cellsWithMask(mask, state, candidates)) {
                            int maskToClear = candidates.get(cell).getMask().get() & ~((Integer) groupWithNMasks.get("Mask"));
                            if (maskToClear == 0)
                                continue;

                            candidates.get(cell).setMask(new Mask(candidates.get(cell).getMask().get() & ((Integer) groupWithNMasks.get("Mask"))));
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
                }

                //endregion
            }
            //region Final attempt - look if the board has multiple solutions
            if (!changeMade) {
                // This is the last chance to do something in this iteration:
                // If this attempt fails, board will not be entirely solved.

                // Try to see if there are pairs of values that can be exchanged arbitrarily
                // This happens when board has more than one valid solution

                Queue<Integer> candidateIndex1 = new LinkedList<>();
                Queue<Integer> candidateIndex2 = new LinkedList<>();
                Queue<Integer> candidateDigit1 = new LinkedList<>();
                Queue<Integer> candidateDigit2 = new LinkedList<>();

                for (int i = 0; i < candidates.size() - 1; i++) {
                    if (candidates.get(i).getMask().candidatesCount() == 2) {
                        int row = i / 9;
                        int col = i % 9;
                        int blockIndex = 3 * (row / 3) + col / 3;

                        int temp = candidates.get(i).getMask().get();
                        int lower = 0;
                        int upper = 0;
                        for (int digit = 1; temp > 0; digit++) {
                            if ((temp & 1) != 0) {
                                lower = upper;
                                upper = digit;
                            }
                            temp = temp >> 1;
                        }

                        for (int j = i + 1; j < candidates.size(); j++) {
                            if (candidates.get(j).getMask().get() == candidates.get(i).getMask().get()) {
                                int row1 = j / 9;
                                int col1 = j % 9;
                                int blockIndex1 = 3 * (row1 / 3) + col1 / 3;

                                if (row == row1 || col == col1 || blockIndex == blockIndex1) {
                                    candidateIndex1.add(i);
                                    candidateIndex2.add(j);
                                    candidateDigit1.add(lower);
                                    candidateDigit2.add(upper);
                                }
                            }
                        }
                    }
                }

                // At this point we have the lists with pairs of cells that might pick one of two digits each
                // Now we have to check whether that is really true - does the board have two solutions?

                List<Integer> stateIndex1 = new ArrayList<>();
                List<Integer> stateIndex2 = new ArrayList<>();
                List<Integer> value1 = new ArrayList<>();
                List<Integer> value2 = new ArrayList<>();

                while (!candidateIndex1.isEmpty()) {
                    int index1 = candidateIndex1.remove();
                    int index2 = candidateIndex2.remove();
                    int digit1 = candidateDigit1.remove();
                    int digit2 = candidateDigit2.remove();


                    State alternateState = state.copy();

                    if (solutionState.get(index1) == digit1) {
                        alternateState.set(index1, digit2);
                        alternateState.set(index2, digit1);
                    } else {
                        alternateState.set(index1, digit1);
                        alternateState.set(index2, digit2);
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
                                                        .mapToInt(sibling -> Masks.maskForDigit(currentState.get(sibling)))
                                                        .reduce(0, (digitsMask, digitMask) -> digitsMask | digitMask);

                                        boolean[] isDigitUsed = new boolean[9];

                                        for (int i = 0; i < 9; i++) {
                                            isDigitUsed[i] = (digitUsedMask & Masks.maskForDigit(i + 1)) != 0;
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
                            stateIndex1.add(index1);
                            stateIndex2.add(index2);
                            value1.add(digit1);
                            value2.add(digit2);
                        }
                    }
                } // while (!candidateIndex1.empty())

                if (!stateIndex1.isEmpty()) {
                    int pos = rng.nextInt(stateIndex1.size());
                    int index1 = stateIndex1.get(pos);
                    int index2 = stateIndex2.get(pos);
                    int digit1 = value1.get(pos);
                    int digit2 = value2.get(pos);
                    int row1 = index1 / 9;
                    int col1 = index1 % 9;
                    int row2 = index2 / 9;
                    int col2 = index2 % 9;

                    String description = "";

                    if (index1 / 9 == index2 / 9) {
                        description = "row #" + (index1 / 9 + 1);
                    } else if (index1 % 9 == index2 % 9) {
                        description = "column #" + (index1 % 9 + 1);
                    } else {
                        description = "block (" + (row1 / 3 + 1) + ", " + (col1 / 3 + 1) + ")";
                    }

                    state.set(index1, solutionState.get(index1));
                    state.set(index2, solutionState.get(index2));
                    candidates.get(index1).setNoCandidates();
                    candidates.get(index2).setNoCandidates();
                    changeMade = true;

                    System.out.println("Guessing that " + digit1 + " and " + digit2 + " are arbitrary in " + description + " (multiple solutions): Pick " + solutionState.get(index1) + "->(" + (row1 + 1) + ", " + (col1 + 1) + "), " + solutionState.get(index2) + "->(" + (row2 + 1) + ", " + (col2 + 1) + ").");
                }
            }
            //endregion

            if (changeMade) {
                //region Print the board as it looks after one change was made to it
                Board board = new Board(state);
                board.printBoard();
                board.printCode();
                System.out.println();
                //endregion
            }
        }
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
                                        .mapToInt(sibling -> Masks.maskForDigit(currentState.get(sibling)))
                                        .reduce(0, (digitsMask, digitMask) -> digitsMask | digitMask);

                        boolean[] isDigitUsed = new boolean[9];

                        for (int i = 0; i < 9; i++) {
                            isDigitUsed[i] = (digitUsedMask & Masks.maskForDigit(i + 1)) != 0;
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

class Change {
    private final Cell cell;
    private final int digit;

    Change(Cell cell, int digit) {
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

    private final List<Candidate> candidates;

    Candidates(State state) {
        this.candidates = calculateFrom(state);
    }

    List<Mask> twoDigitMasks() {
        return candidates.stream()
                .map(candidate -> candidate.getMask())
                .filter(mask -> mask.candidatesCount() == 2)
                .distinct()
                .collect(toList());
    }

    List<Candidate> singleCandidates() {
        return candidates.stream()
                .filter(candidate -> candidate.getMask().candidatesCount() == 1)
                .collect(toList());
    }

    private static List<Candidate> calculateFrom(State state) {
        return IntStream.range(0, state.size())
                .mapToObj(i -> {
                    if (state.hasValue(i)) {
                        return new NoCandidate(Cell.of(i));
                    }
                    int collidingDigitsMask =
                            Cell.of(i).allSiblings().stream()
                                    .mapToInt(sibling -> Masks.maskForDigit(state.get(sibling)))
                                    .reduce(0, (digitsMask, digitMask) -> digitsMask | digitMask);
                    return new Candidate(Cell.of(i), new Mask(allOnes & ~collidingDigitsMask));
                }).collect(toList());
    }

    @Override
    public Candidate get(int index) {
        return candidates.get(index);
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

    Integer singleDigit() {
        return singleBitMaskToDigit.get(get());
    }

    int candidatesCount() {
        return maskToOnesCount.get(get());
    }

    public int get() {
        return mask;
    }

    private static Map<Integer, Integer> singleBitMaskToDigit() {
        Map<Integer, Integer> singleBitMaskToDigit = new HashMap<>();
        for (int i = 0; i < 9; i++)
            singleBitMaskToDigit.put(1 << i, i + 1);

        return singleBitMaskToDigit;
    }
}

class Masks {
    static final List<Integer> nMasks = nMasks();

    static int maskForDigit(int i) {
        return 1 << (i - 1);
    }

    // masks that represent two or more candidates
    private static List<Integer> nMasks() {
        return Mask.maskToOnesCount.entrySet().stream()
                .filter(tuple -> tuple.getValue() > 1)
                .map(tuple -> tuple.getKey())
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

class MaskGroup {
    private final int mask;
    private final CellGroup group;

    public MaskGroup(int mask, CellGroup group) {
        this.mask = mask;
        this.group = group;
    }

    public int getMask() {
        return mask;
    }

    public CellGroup getGroup() {
        return group;
    }
}

class CellGroup extends AbstractList<Cell> {
    private static final List<CellGroup> cellGroups = buildCellGroups();

    private final String description;
    private final List<Cell> cells;

    private CellGroup(String description, List<Cell> cells) {
        this.description = description;
        this.cells = Collections.unmodifiableList(cells);
    }

    static List<CellGroup> all() {
        return cellGroups;
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
        var rowCellGroups =
                Cell.cells().stream()
                        .collect(groupingBy(Cell::getRow))
                        .entrySet().stream()
                        .map(group -> new CellGroup(
                                "row #" + (group.getKey() + 1),
                                group.getValue()))
                        .collect(toList());

        var columnCellGroups =
                Cell.cells().stream()
                        .collect(groupingBy(Cell::getColumn))
                        .entrySet().stream()
                        .map(group -> new CellGroup(
                                "column #" + (group.getKey() + 1),
                                group.getValue()))
                        .collect(toList());

        var blockCellGroups =
                Cell.cells().stream()
                        .collect(groupingBy(Cell::getBlock))
                        .entrySet().stream()
                        .map(group -> new CellGroup(
                                format("block (%s, %s)", group.getKey() / 3 + 1, group.getKey() % 3 + 1),
                                group.getValue()))
                        .collect(toList());

        var cellGroupsList =
                Stream.of(rowCellGroups, columnCellGroups, blockCellGroups)
                        .flatMap(Collection::stream)
                        .collect(toList());

        return Collections.unmodifiableList(cellGroupsList);
        //endregion
    }

    public List<Cell> cellsWithMask(int mask, State state, Candidates candidates) {
        return this.stream()
                .filter(cell -> state.get(cell) == 0 && (candidates.get(cell).getMask().get() & mask) != 0)
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