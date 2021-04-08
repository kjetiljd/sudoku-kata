package sudoku.kata;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut;

class ProgramTest {

    @Test
    void playTest() throws Exception {
        String actual = tapSystemOut(() ->
                Program.play(new Random(1))
        );
        Approvals.verify(actual);
    }
}