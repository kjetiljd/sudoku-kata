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

    static void play(Random random) {
        //region Construct fully populated board
        // Prepare empty board
        String line = "+---+---+---+";
        String middle = "|...|...|...|";
        char[][] board = new char[][]
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

        // Construct board to be solved
        Random rng = random;

        // Top element is current state of the board
        Stack<int[]> stateStack = new Stack<>();

        // Top elements are (row, col) of cell which has been modified compared to previous state
        Stack<Integer> rowIndexStack = new Stack<Integer>();
        Stack<Integer> colIndexStack = new Stack<Integer>();

        // Top element indicates candidate digits (those with False) for (row, col)
        Stack<boolean[]> usedDigitsStack = new Stack<boolean[]>();

        // Top element is the value that was set on (row, col)
        Stack<Integer> lastDigitStack = new Stack<Integer>();

        // Indicates operation to perform next
        // - expand - finds next empty cell and puts new state on stacks
        // - move - finds next candidate number at current pos and applies it to current state
        // - collapse - pops current state from stack as it did not yield a solution
        String command = "expand";
        while (stateStack.size() <= 9 * 9) {
            if (command.equals("expand")) {
                int[] currentState = new int[9 * 9];

                if (stateStack.size() > 0) {
                    System.arraycopy(stateStack.peek(), 0, currentState, 0, currentState.length);
                }

                int bestRow = -1;
                int bestCol = -1;
                boolean[] bestUsedDigits = null;
                int bestCandidatesCount = -1;
                int bestRandomValue = -1;
                boolean containsUnsolvableCells = false;

                for (int index = 0; index < currentState.length; index++)
                    if (currentState[index] == 0) {

                        int row = index / 9;
                        int col = index % 9;
                        int blockRow = row / 3;
                        int blockCol = col / 3;

                        boolean[] isDigitUsed = new boolean[9];

                        for (int i = 0; i < 9; i++) {
                            int rowDigit = currentState[9 * i + col];
                            if (rowDigit > 0)
                                isDigitUsed[rowDigit - 1] = true;

                            int colDigit = currentState[9 * row + i];
                            if (colDigit > 0)
                                isDigitUsed[colDigit - 1] = true;

                            int blockDigit = currentState[(blockRow * 3 + i / 3) * 9 + (blockCol * 3 + i % 3)];
                            if (blockDigit > 0)
                                isDigitUsed[blockDigit - 1] = true;
                        } // for (i = 0..8)

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
                            bestRow = row;
                            bestCol = col;
                            bestUsedDigits = isDigitUsed;
                            bestCandidatesCount = candidatesCount;
                            bestRandomValue = randomValue;
                        }

                    } // for (index = 0..81)

                if (!containsUnsolvableCells) {
                    stateStack.push(currentState);
                    rowIndexStack.push(bestRow);
                    colIndexStack.push(bestCol);
                    usedDigitsStack.push(bestUsedDigits);
                    lastDigitStack.push(0); // No digit was tried at this position
                }

                // Always try to move after expand
                command = "move";

            } // if (command == "expand")
            else if (command.equals("collapse")) {
                stateStack.pop();
                rowIndexStack.pop();
                colIndexStack.pop();
                usedDigitsStack.pop();
                lastDigitStack.pop();

                command = "move";   // Always try to move after collapse
            } else if (command.equals("move")) {

                int rowToMove = rowIndexStack.peek();
                int colToMove = colIndexStack.peek();
                int digitToMove = lastDigitStack.pop();

                int rowToWrite = rowToMove + rowToMove / 3 + 1;
                int colToWrite = colToMove + colToMove / 3 + 1;

                boolean[] usedDigits = usedDigitsStack.peek();
                int[] currentState = stateStack.peek();
                int currentStateIndex = 9 * rowToMove + colToMove;

                int movedToDigit = digitToMove + 1;
                while (movedToDigit <= 9 && usedDigits[movedToDigit - 1])
                    movedToDigit += 1;

                if (digitToMove > 0) {
                    usedDigits[digitToMove - 1] = false;
                    currentState[currentStateIndex] = 0;
                    board[rowToWrite][colToWrite] = '.';
                }

                if (movedToDigit <= 9) {
                    lastDigitStack.push(movedToDigit);
                    usedDigits[movedToDigit - 1] = true;
                    currentState[currentStateIndex] = movedToDigit;
                    board[rowToWrite][colToWrite] = (char) ('0' + movedToDigit);

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

        System.out.println();
        System.out.println("Final look of the solved board:");
        System.out.println(String.join(System.lineSeparator(), Arrays.stream(board).map(it -> new String(it)).collect(toList())));
        //endregion

        //region Generate inital board from the completely solved one
        // Board is solved at this point.
        // Now pick subset of digits as the starting position.
        int remainingDigits = 30;
        int maxRemovedPerBlock = 6;
        int[][] removedPerBlock = new int[3][3];
        int[] positions = IntStream.range(0, 9 * 9).toArray();
        int[] state = stateStack.peek();

        int[] finalState = new int[state.length];
        System.arraycopy(state, 0, finalState, 0, finalState.length);

        int removedPos = 0;
        while (removedPos < 9 * 9 - remainingDigits) {
            int curRemainingDigits = positions.length - removedPos;
            int indexToPick = removedPos + rng.nextInt(curRemainingDigits);

            int row = positions[indexToPick] / 9;
            int col = positions[indexToPick] % 9;

            int blockRowToRemove = row / 3;
            int blockColToRemove = col / 3;

            if (removedPerBlock[blockRowToRemove][blockColToRemove] >= maxRemovedPerBlock)
                continue;

            removedPerBlock[blockRowToRemove][blockColToRemove] += 1;

            int temp = positions[removedPos];
            positions[removedPos] = positions[indexToPick];
            positions[indexToPick] = temp;

            int rowToWrite = row + row / 3 + 1;
            int colToWrite = col + col / 3 + 1;

            board[rowToWrite][colToWrite] = '.';

            int stateIndex = 9 * row + col;
            state[stateIndex] = 0;

            removedPos += 1;
        }

        System.out.println();
        System.out.println("Starting look of the board to solve:");
        System.out.println(String.join("\n", Arrays.stream(board).map(it -> new String(it)).collect(toList())));
        //endregion

        //region Prepare lookup structures that will be used in further execution
        System.out.println();
        System.out.println("=".repeat(80));
        System.out.println();

        Map<Integer, Integer> maskToOnesCount = new HashMap<Integer, Integer>();
        maskToOnesCount.put(0, 0);
        for (int i = 1; i < (1 << 9); i++) {
            int smaller = i >> 1;
            int increment = i & 1;
            maskToOnesCount.put(i, maskToOnesCount.get(smaller) + increment);
        }

        Map<Integer, Integer> singleBitToIndex = new HashMap<Integer, Integer>();
        for (int i = 0; i < 9; i++)
            singleBitToIndex.put(1 << i, i);

        //endregion

        boolean changeMade = true;
        while (changeMade) {
            changeMade = false;

            int[] candidateMasks = calculateCandidates(state);

            //region Build a collection (named cellGroups) which maps cell indices into distinct groups (rows/columns/blocks)

            var cellGroups = buildCellGroups();
            //endregion

            boolean stepChangeMade = true;
            while (stepChangeMade) {
                stepChangeMade = false;

                //region Pick cells with only one candidate left

                int[] singleCandidateIndices =
                        IntStream.range(0, candidateMasks.length)
                                .filter(index -> maskToOnesCount.get(candidateMasks[index]) == 1)
                                .toArray();


                if (singleCandidateIndices.length > 0) {
                    int pickSingleCandidateIndex = rng.nextInt(singleCandidateIndices.length);
                    int singleCandidateIndex = singleCandidateIndices[pickSingleCandidateIndex];
                    int candidateMask = candidateMasks[singleCandidateIndex];
                    int candidate = singleBitToIndex.get(candidateMask);

                    Cell singleCandidate = Cell.of(singleCandidateIndex);

                    int row = singleCandidate.getRow();
                    int col = singleCandidate.getColumn();

                    int rowToWrite = row + row / 3 + 1;
                    int colToWrite = col + col / 3 + 1;

                    state[singleCandidateIndex] = candidate + 1;
                    board[rowToWrite][colToWrite] = (char) ('1' + candidate);
                    candidateMasks[singleCandidateIndex] = 0;
                    changeMade = true;

                    System.out.format("(%s, %s) can only contain %s.", row + 1, col + 1, candidate + 1).println();
                }

                //endregion

                //region Try to find a number which can only appear in one place in a row/column/block

                if (!changeMade) {
                    List<String> groupDescriptions = new ArrayList<String>();
                    List<Integer> candidateRowIndices = new ArrayList<Integer>();
                    List<Integer> candidateColIndices = new ArrayList<Integer>();
                    List<Integer> candidates = new ArrayList<Integer>();

                    for (int digit = 1; digit <= 9; digit++) {
                        int mask = maskForDigit(digit);
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

                                if ((candidateMasks[rowStateIndex] & mask) != 0) {
                                    rowNumberCount += 1;
                                    indexInRow = indexInGroup;
                                }

                                if ((candidateMasks[colStateIndex] & mask) != 0) {
                                    colNumberCount += 1;
                                    indexInCol = indexInGroup;
                                }

                                if ((candidateMasks[blockStateIndex] & mask) != 0) {
                                    blockNumberCount += 1;
                                    indexInBlock = indexInGroup;
                                }
                            }

                            if (rowNumberCount == 1) {
                                groupDescriptions.add("Row #" + (cellGroup + 1));
                                candidateRowIndices.add(cellGroup);
                                candidateColIndices.add(indexInRow);
                                candidates.add(digit);
                            }

                            if (colNumberCount == 1) {
                                groupDescriptions.add("Column #" + (cellGroup + 1));
                                candidateRowIndices.add(indexInCol);
                                candidateColIndices.add(cellGroup);
                                candidates.add(digit);
                            }

                            if (blockNumberCount == 1) {
                                int blockRow = cellGroup / 3;
                                int blockCol = cellGroup % 3;

                                groupDescriptions.add("Block (" + (blockRow + 1) + ", " + (blockCol + 1) + ")");
                                candidateRowIndices.add(blockRow * 3 + indexInBlock / 3);
                                candidateColIndices.add(blockCol * 3 + indexInBlock % 3);
                                candidates.add(digit);
                            }
                        } // for (cellGroup = 0..8)
                    } // for (digit = 1..9)

                    if (candidates.size() > 0) {
                        int index = rng.nextInt(candidates.size());
                        String description = groupDescriptions.get(index);
                        int row = candidateRowIndices.get(index);
                        int col = candidateColIndices.get(index);
                        int digit = candidates.get(index);
                        int rowToWrite = row + row / 3 + 1;
                        int colToWrite = col + col / 3 + 1;

                        String message = description + " can contain " + digit + " only at (" + (row + 1) + ", " + (col + 1) + ").";

                        int stateIndex = 9 * row + col;
                        state[stateIndex] = digit;
                        candidateMasks[stateIndex] = 0;
                        board[rowToWrite][colToWrite] = (char) ('0' + digit);

                        changeMade = true;

                        System.out.println(message);
                    }
                }

                //endregion

                //region Try to find pairs of digits in the same row/column/block and remove them from other colliding cells
                if (!changeMade) {

                    var twoDigitMasks = IntStream.range(0, candidateMasks.length)
                            .map(index -> candidateMasks[index])
                            .filter(mask -> maskToOnesCount.get(mask) == 2)
                            .distinct().toArray();

                    var groups =
                            IntStream.range(0, twoDigitMasks.length)
                                    .mapToObj(maskIndex ->
                                            cellGroups.stream()
                                                    .filter(group -> group.getValue().stream()
                                                            .filter(tuple -> candidateMasks[(Integer) tuple.get("Index")] == twoDigitMasks[maskIndex]).count() == 2)
                                                    .filter(group -> group.getValue().stream()
                                                            .anyMatch(tuple -> candidateMasks[(Integer) tuple.get("Index")] != twoDigitMasks[maskIndex]
                                                                    && (candidateMasks[(Integer) tuple.get("Index")] & twoDigitMasks[maskIndex]) > 0))
                                                    .map(group ->
                                                            Map.of(
                                                                    "Mask", twoDigitMasks[maskIndex],
                                                                    "Discriminator", group.getKey(),
                                                                    "Description", group.getDescription(),
                                                                    "Cells", group.getValue()
                                                            )
                                                    ).collect(toList())
                                    ).flatMap(Collection::stream)
                                    .collect(toList());

                    if (!groups.isEmpty()) {
                        for (var group : groups) {
                            var cells = ((List<Cell>) group.get("Cells"))
                                    .stream().filter(cell ->
                                            candidateMasks[(int) cell.get("Index")] != (int) group.get("Mask")
                                                    && (candidateMasks[(int) cell.get("Index")] & (int) group.get("Mask")) > 0)
                                    .collect(toList());

                            var maskCells = ((List<Cell>) group.get("Cells"))
                                    .stream().filter(cell ->
                                            candidateMasks[(int) cell.get("Index")] == (int) group.get("Mask"))
                                    .collect(toList());

                            if (!cells.isEmpty()) {
                                int upper = 0;
                                int lower = 0;
                                int temp = (int) group.get("Mask");

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
                                        "Values " + lower + " and " + upper + " in " + group.get("Description") +
                                                " are in cells (" + ((int) maskCells.get(0).get("Row") + 1) + ", " + ((int) maskCells.get(0).get("Column") + 1) + ")" +
                                                " and (" + ((int) maskCells.get(1).get("Row") + 1) + ", " + ((int) maskCells.get(1).get("Column") + 1) + ").");

                                for (var cell : cells) {
                                    int maskToRemove = candidateMasks[(int) cell.get("Index")] & (int) group.get("Mask");
                                    List<Integer> valuesToRemove = new ArrayList<Integer>();
                                    int curValue = 1;
                                    while (maskToRemove > 0) {
                                        if ((maskToRemove & 1) > 0) {
                                            valuesToRemove.add(curValue);
                                        }
                                        maskToRemove = maskToRemove >> 1;
                                        curValue += 1;
                                    }

                                    String valuesReport = String.join(", ", valuesToRemove.stream().map(Object::toString).collect(Collectors.toList()));
                                    System.out.println(valuesReport + " cannot appear in (" + ((int) cell.get("Row") + 1) + ", " + ((int) cell.get("Column") + 1) + ").");

                                    candidateMasks[(int) cell.get("Index")] &= ~(int) group.get("Mask");
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

                    var masks =
                            maskToOnesCount.entrySet().stream()
                                    .filter(tuple -> tuple.getValue() > 1)
                                    .map(tuple -> tuple.getKey())
                                    .collect(toList());

                    var groupsWithNMasks =
                            masks.stream()
                                    .map(mask -> cellGroups.stream()
                                            .filter(group -> group.getValue().stream().allMatch(cell ->
                                                    state[(Integer) cell.get("Index")] == 0 || (mask & (maskForDigit(state[(Integer) cell.get("Index")]))) == 0))
                                            .map(group ->
                                                    Map.of(
                                                            "Mask", mask,
                                                            "Description", group.getDescription(),
                                                            "Cells", group.getValue(),
                                                            "CellsWithMask",
                                                            group.getValue().stream()
                                                                    .filter(cell -> state[(Integer) cell.get("Index")] == 0 && (candidateMasks[(Integer) cell.get("Index")] & mask) != 0)
                                                                    .collect(toList()),
                                                            "CleanableCellsCount",
                                                            (int) group.getValue().stream()
                                                                    .filter(cell -> {
                                                                        int cellIndex = (Integer) cell.get("Index");
                                                                        return (state[cellIndex] == 0) &&
                                                                                ((candidateMasks[cellIndex] & mask) != 0) &&
                                                                                ((candidateMasks[cellIndex] & ~mask) != 0);
                                                                    })
                                                                    .count()))
                                            .filter(group -> ((List) (group.get("CellsWithMask"))).size() == maskToOnesCount.get((Integer) group.get("Mask")))
                                            .collect(toList()))
                                    .flatMap(Collection::stream)
                                    .collect(toList());


                    for (var groupWithNMasks : groupsWithNMasks) {
                        int mask = (int) groupWithNMasks.get("Mask");

                        if (((List<Cell>) groupWithNMasks.get("Cells")).stream()
                                .anyMatch(cell ->
                                        ((candidateMasks[(Integer) cell.get("Index")] & mask) != 0) &&
                                                ((candidateMasks[(Integer) cell.get("Index")] & ~mask) != 0))) {
                            StringBuilder message = new StringBuilder();
                            message.append("In " + groupWithNMasks.get("Description") + " values ");

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
                            for (var cell : ((List<Cell>) groupWithNMasks.get("CellsWithMask"))) {
                                message.append(" (" + ((Integer) cell.get("Row") + 1) + ", " + ((Integer) cell.get("Column") + 1) + ")");
                            }

                            message.append(" and other values cannot appear in those cells.");

                            System.out.println(message.toString());
                        }

                        for (var cell : ((List<Cell>) groupWithNMasks.get("CellsWithMask"))) {
                            int maskToClear = candidateMasks[(Integer) cell.get("Index")] & ~((Integer) groupWithNMasks.get("Mask"));
                            if (maskToClear == 0)
                                continue;

                            candidateMasks[(Integer) cell.get("Index")] &= ((Integer) groupWithNMasks.get("Mask"));
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

                            message.append(" cannot appear in cell (" + ((int) cell.get("Row") + 1) + ", " + ((int) cell.get("Column") + 1) + ").");
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

                Queue<Integer> candidateIndex1 = new LinkedList<Integer>();
                Queue<Integer> candidateIndex2 = new LinkedList<Integer>();
                Queue<Integer> candidateDigit1 = new LinkedList<Integer>();
                Queue<Integer> candidateDigit2 = new LinkedList<Integer>();

                for (int i = 0; i < candidateMasks.length - 1; i++) {
                    if (maskToOnesCount.get(candidateMasks[i]) == 2) {
                        int row = i / 9;
                        int col = i % 9;
                        int blockIndex = 3 * (row / 3) + col / 3;

                        int temp = candidateMasks[i];
                        int lower = 0;
                        int upper = 0;
                        for (int digit = 1; temp > 0; digit++) {
                            if ((temp & 1) != 0) {
                                lower = upper;
                                upper = digit;
                            }
                            temp = temp >> 1;
                        }

                        for (int j = i + 1; j < candidateMasks.length; j++) {
                            if (candidateMasks[j] == candidateMasks[i]) {
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

                List<Integer> stateIndex1 = new ArrayList<Integer>();
                List<Integer> stateIndex2 = new ArrayList<Integer>();
                List<Integer> value1 = new ArrayList<Integer>();
                List<Integer> value2 = new ArrayList<Integer>();

                while (!candidateIndex1.isEmpty()) {
                    int index1 = candidateIndex1.remove();
                    int index2 = candidateIndex2.remove();
                    int digit1 = candidateDigit1.remove();
                    int digit2 = candidateDigit2.remove();

                    int[] alternateState = new int[finalState.length];
                    System.arraycopy(state, 0, alternateState, 0, alternateState.length);

                    if (finalState[index1] == digit1) {
                        alternateState[index1] = digit2;
                        alternateState[index2] = digit1;
                    } else {
                        alternateState[index1] = digit1;
                        alternateState[index2] = digit2;
                    }

                    // What follows below is a complete copy-paste of the solver which appears at the beginning of this method
                    // However, the algorithm couldn't be applied directly and it had to be modified.
                    // Implementation below assumes that the board might not have a solution.
                    stateStack = new Stack<>();
                    rowIndexStack = new Stack<Integer>();
                    colIndexStack = new Stack<Integer>();
                    usedDigitsStack = new Stack<boolean[]>();
                    lastDigitStack = new Stack<Integer>();

                    command = "expand";
                    while (!command.equals("complete") && !command.equals("fail")) {
                        if (command.equals("expand")) {
                            int[] currentState = new int[9 * 9];

                            if (!stateStack.isEmpty()) {
                                System.arraycopy(stateStack.peek(), 0, currentState, 0, currentState.length);
                            } else {
                                System.arraycopy(alternateState, 0, currentState, 0, currentState.length);
                            }

                            int bestRow = -1;
                            int bestCol = -1;
                            boolean[] bestUsedDigits = null;
                            int bestCandidatesCount = -1;
                            int bestRandomValue = -1;
                            boolean containsUnsolvableCells = false;

                            for (int index = 0; index < currentState.length; index++)
                                if (currentState[index] == 0) {

                                    int row = index / 9;
                                    int col = index % 9;
                                    int blockRow = row / 3;
                                    int blockCol = col / 3;

                                    boolean[] isDigitUsed = new boolean[9];

                                    for (int i = 0; i < 9; i++) {
                                        int rowDigit = currentState[9 * i + col];
                                        if (rowDigit > 0)
                                            isDigitUsed[rowDigit - 1] = true;

                                        int colDigit = currentState[9 * row + i];
                                        if (colDigit > 0)
                                            isDigitUsed[colDigit - 1] = true;

                                        int blockDigit = currentState[(blockRow * 3 + i / 3) * 9 + (blockCol * 3 + i % 3)];
                                        if (blockDigit > 0)
                                            isDigitUsed[blockDigit - 1] = true;
                                    } // for (i = 0..8)


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
                                        bestRow = row;
                                        bestCol = col;
                                        bestUsedDigits = isDigitUsed;
                                        bestCandidatesCount = candidatesCount;
                                        bestRandomValue = randomValue;
                                    }

                                } // for (index = 0..81)

                            if (!containsUnsolvableCells) {
                                stateStack.push(currentState);
                                rowIndexStack.push(bestRow);
                                colIndexStack.push(bestCol);
                                usedDigitsStack.push(bestUsedDigits);
                                lastDigitStack.push(0); // No digit was tried at this position
                            }

                            // Always try to move after expand
                            command = "move";

                        } // if (command == "expand")
                        else if (command.equals("collapse")) {
                            stateStack.pop();
                            rowIndexStack.pop();
                            colIndexStack.pop();
                            usedDigitsStack.pop();
                            lastDigitStack.pop();

                            if (!stateStack.empty())
                                command = "move"; // Always try to move after collapse
                            else
                                command = "fail";
                        } else if (command.equals("move")) {

                            int rowToMove = rowIndexStack.peek();
                            int colToMove = colIndexStack.peek();
                            int digitToMove = lastDigitStack.pop();

                            int rowToWrite = rowToMove + rowToMove / 3 + 1;
                            int colToWrite = colToMove + colToMove / 3 + 1;

                            boolean[] usedDigits = usedDigitsStack.peek();
                            int[] currentState = stateStack.peek();
                            int currentStateIndex = 9 * rowToMove + colToMove;

                            int movedToDigit = digitToMove + 1;
                            while (movedToDigit <= 9 && usedDigits[movedToDigit - 1])
                                movedToDigit += 1;

                            if (digitToMove > 0) {
                                usedDigits[digitToMove - 1] = false;
                                currentState[currentStateIndex] = 0;
                                board[rowToWrite][colToWrite] = '.';
                            }

                            if (movedToDigit <= 9) {
                                lastDigitStack.push(movedToDigit);
                                usedDigits[movedToDigit - 1] = true;
                                currentState[currentStateIndex] = movedToDigit;
                                board[rowToWrite][colToWrite] = (char) ('0' + movedToDigit);

                                if (Arrays.stream(currentState).anyMatch(digit -> digit == 0))
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

                    state[index1] = finalState[index1];
                    state[index2] = finalState[index2];
                    candidateMasks[index1] = 0;
                    candidateMasks[index2] = 0;
                    changeMade = true;

                    for (int i = 0; i < state.length; i++) {
                        int tempRow = i / 9;
                        int tempCol = i % 9;
                        int rowToWrite = tempRow + tempRow / 3 + 1;
                        int colToWrite = tempCol + tempCol / 3 + 1;

                        board[rowToWrite][colToWrite] = '.';
                        if (state[i] > 0)
                            board[rowToWrite][colToWrite] = (char) ('0' + state[i]);
                    }

                    System.out.println("Guessing that " + digit1 + " and " + digit2 + " are arbitrary in " + description + " (multiple solutions): Pick " + finalState[index1] + "->(" + (row1 + 1) + ", " + (col1 + 1) + "), " + finalState[index2] + "->(" + (row2 + 1) + ", " + (col2 + 1) + ").");
                }
            }
            //endregion

            if (changeMade) {
                //region Print the board as it looks after one change was made to it
                System.out.println(String.join(System.lineSeparator(), Arrays.stream(board).map(it -> new String(it)).collect(toList())));
                ;
                String code =
                        String.join("", Arrays.stream(board).map(it -> new String(it)).collect(toList()))
                                .replace("-", "")
                                .replace("+", "")
                                .replace("|", "")
                                .replace(".", "0");
                System.out.format("Code: %s", code).println();
                System.out.println();
                //endregion
            }
        }
    }

    private static List<CellGroup> buildCellGroups() {
        var rowCellGroups =
                Cell.cells().stream()
                        .collect(groupingBy(Cell::getRow))
                        .entrySet().stream()
                        .map(group -> new CellGroup(
                                group.getKey(),
                                "row #" + (group.getKey() + 1),
                                group.getValue()))
                        .collect(toList());

        var columnCellGroups =
                Cell.cells().stream()
                        .collect(groupingBy(Cell::getColumn))
                        .entrySet().stream()
                        .map(group -> new CellGroup(
                                9 + group.getKey(),
                                "column #" + (group.getKey() + 1),
                                group.getValue()))
                        .collect(toList());

        var blockCellGroups =
                Cell.cells().stream()
                        .collect(groupingBy(Cell::getBlock))
                        .entrySet().stream()
                        .map(group -> new CellGroup(
                                18 + group.getKey(),
                                format("block (%s, %s)", group.getKey() / 3 + 1, group.getKey() % 3 + 1),
                                group.getValue()))
                        .collect(toList());

        var cellGroupsList =
                Stream.of(rowCellGroups, columnCellGroups, blockCellGroups)
                        .flatMap(Collection::stream)
                        .collect(toList());

        return cellGroupsList;
    }

    private static int[] calculateCandidates(int[] state) {
        int allOnes = (1 << 9) - 1;

        int[] candidateMasks = new int[state.length];

        for (int i = 0; i < state.length; i++) {
            Cell cell = Cell.of(i);
            if (state[i] == 0) {
                int collidingDigitsMask =
                        cell.allSiblings().stream()
                                .mapToInt(sibling -> maskForDigit(state[sibling.getIndex()]))
                                .reduce(0, (digitsMask, digitMask) -> digitsMask | digitMask);

                candidateMasks[i] = allOnes & ~collidingDigitsMask;
            }
        }
        return candidateMasks;
    }

    private static int maskForDigit(int i) {
        return 1 << (i - 1);
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

class CellGroup extends AbstractList<Cell> {
    private final int discriminator;
    private final String description;

    private final List<Cell> cells;

    CellGroup(int discriminator, String description, List<Cell> cells) {
        this.discriminator = discriminator;
        this.description = description;
        this.cells = Collections.unmodifiableList(cells);
    }

    public int getDiscriminator() {
        return discriminator;
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

    public List<Cell> getCells() {
        return cells;
    }

    /* temp method during conversion */
    Integer getKey() {
        return getDiscriminator();
    }

    /* temp method during conversion */
    List<Cell> getValue() {
        return getCells();
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

    private int index;

    private Cell(int index) {
        this.index = index;
    }

    static Cell of(int index) {
        return cells[index];
    }

    /* temp method during conversion */
    Object get(String field) {
        switch (field) {
            case "Index":
                return getIndex();
            case "Row":
                return getRow();
            case "Column":
                return getColumn();
            default:
                throw new UnsupportedOperationException();
        }
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
}