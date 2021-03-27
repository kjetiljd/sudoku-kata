package sudoku.kata;

import org.approvaltests.Approvals;
import org.approvaltests.core.Options;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;

import java.util.Random;

import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut;
import static sudoku.kata.Program.play;

class ProgramTest {

    @RepeatedTest(value = 20, name = "{displayName} {currentRepetition}/{totalRepetitions}")
    @DisplayName("playTest")
    void playTest(RepetitionInfo repetitionInfo) throws Exception {
        int seed = repetitionInfo.getCurrentRepetition();
        var actual = tapSystemOut( () -> {
            play(new Random(seed));
        });

        Options seedExtension = new Options().forFile().withExtension("." + seed + ".txt");
        Approvals.verify(actual, seedExtension);
    }
}