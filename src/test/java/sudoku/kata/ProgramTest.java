package sudoku.kata;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut;

class ProgramTest {

    @Test
    void playTest() throws Exception {
        String actual = tapSystemOut(() ->
                {
                    for (int i = 0; i < 20; i++) {
                        Program.play(new Random(i));
                    }
                }
        );
        Approvals.verify(actual);
    }
}