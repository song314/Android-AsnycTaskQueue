package cn.mianyang.song314.async.taskqueue;

import android.content.Context;
import android.test.InstrumentationTestCase;

import java.io.File;

/**
 * time: 12/2/15
 * description:
 *
 * @author tangsong
 */
public class BaseInstrumentationTestCase extends InstrumentationTestCase {

    InstrumentationTestCase mTestCase;
    Context mAppContext;
    Context mTestContext;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mAppContext = getInstrumentation().getTargetContext();
        mTestContext = getInstrumentation().getContext();

        mTestCase = this;
    }

    final void assertFileExits(String file) {
        assertTrue(new File(file).exists());
    }

    void autoWait() {
        synchronized (mTestCase) {
            try {
                mTestCase.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    void autoNotify() {
        synchronized (mTestCase) {
            mTestCase.notify();
        }

    }

}
