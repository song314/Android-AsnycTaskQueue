package cn.mianyang.song314.async.taskqueue;

import android.os.SystemClock;

import com.orhanobut.logger.Logger;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import rx.Observable;
import rx.Subscription;
import rx.schedulers.Schedulers;

/**
 * time: 12/4/15
 * description:
 *
 * @author tangsong
 */
public class AsyncTaskQueueTest extends BaseInstrumentationTestCase {

    private long mIndex = 0;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mIndex = 0;
    }

    public void testCompleted() {

        boolean enableErrorTask = false;

        Logger.init("TestAsyncTaskQueue").hideThreadInfo().methodOffset(0);

        Logger.d(" test start !!!!");

        LinkedList<AsyncTaskQueue.BaseTask> realNormalList = new LinkedList<>();
        LinkedList<AsyncTaskQueue.BaseTask> realErrorList = new LinkedList<>();
        LinkedList<AsyncTaskQueue.BaseTask> realDeleteList = new LinkedList<>();

        LinkedList<AsyncTaskQueue.BaseTask> testDeleteList = new LinkedList<>();
        LinkedList<AsyncTaskQueue.BaseTask> testNormalList = new LinkedList<>();
        LinkedList<AsyncTaskQueue.BaseTask> testErrorList = new LinkedList<>();


        final AsyncTaskQueue queue = new AsyncTaskQueue();
        queue.setAllListener(() -> smartNotify());
        queue.setEachListener(task -> realNormalList.add((AsyncTaskQueue.BaseTask) task));
        queue.setErrorListener((errorTask, e) -> realErrorList.add((AsyncTaskQueue.BaseTask) errorTask));
        queue.onThreadDead(() -> smartNotify());


        for (int i = 0; i < 3; i++) {
            // add normal task
            LinkedList<AsyncTaskQueue.BaseTask> normalTask10 = getNormalTask10();

            testDeleteList.add(normalTask10.getFirst());
            testNormalList.addAll(normalTask10);

            // add error task
            LinkedList<AsyncTaskQueue.BaseTask> errorList = getErrorTask2();

            if (enableErrorTask) {
                testDeleteList.add(errorList.getFirst());
            }
            testErrorList.addAll(errorList);
        }


        checkTheSame(testNormalList);
        checkTheSame(testErrorList);
        checkTheSame(testDeleteList);

        // add into task
        getIoObservable(testNormalList)
                .subscribe(baseTask -> queue.add(baseTask));

        if (enableErrorTask) {
            getIoObservable(testErrorList)
                    .subscribe(task -> queue.add(task));
        }

        // delete task
        getIoObservable(testDeleteList)
                .delay(10, TimeUnit.MILLISECONDS)
                .subscribe(delete -> {
                    if (queue.remove(delete)) {
                        realDeleteList.add(delete);
                    }
                });

        smartWait();

        queue.destroy();

        AtomicBoolean timeOut = new AtomicBoolean(false);
        final Subscription sb = Observable.just("")
                .delay(5, TimeUnit.SECONDS)
                .subscribe(s -> timeOut.set(true));


        synchronized (mTestCase) {
            if (!queue.isAllTaskFinish()) {
                try {
                    Logger.i(" smart waiting 22222 : " + ++mIndex);
                    mTestCase.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                sb.unsubscribe();
                assertFalse(timeOut.get());
            }
        }


        assertTrue(realNormalList.size() == testNormalList.size() - realDeleteList.size());

//        assertTrue(realErrorList.size() == testErrorList.size());
//        assertTrue(realNormalList.size() == testNormalList.size() -);
//        Logger.i(realNormalList.size() + "/" + testNormalList.size() + ", " + realErrorList.size() + );
//        assertTrue(realErrorList.size() + realNormalList.size() == testErrorList.size() + testNormalList.size() - realDeleteList.size());
//
//        Observable.from(realErrorList)
//                .subscribe(task -> assertFalse(realNormalList.contains(task)));

        realDeleteList.clear();
        realErrorList.clear();
        realNormalList.clear();
        testDeleteList.clear();
        testErrorList.clear();
        testNormalList.clear();

        Logger.d(" test finish !!!!");

    }

    private Observable<AsyncTaskQueue.BaseTask> getIoObservable(LinkedList<AsyncTaskQueue.BaseTask> testNormalList) {
        return Observable.from(testNormalList)
                .subscribeOn(Schedulers.io());
    }

    private void smartNotify() {
        synchronized (mTestCase) {
            Logger.i(" smart notify .....");
            mTestCase.notify();
        }
    }

    private void smartWait() {
        try {
            synchronized (mTestCase) {
                mTestCase.wait();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void testCompleteed10000() {
        for (int i = 0; i < 10000; i++) {
            testCompleted();
        }
    }

    private void checkTheSame(LinkedList<AsyncTaskQueue.Task> taskLinkedList, HashSet<AsyncTaskQueue.Task> set) {
        for (AsyncTaskQueue.Task task : taskLinkedList) {
            assertTrue(set.add(task));
        }
    }

    private void checkTheSame(LinkedList<AsyncTaskQueue.BaseTask> taskLinkedList) {
        HashSet<AsyncTaskQueue.BaseTask> set = new HashSet<>();

        for (AsyncTaskQueue.BaseTask task : taskLinkedList) {
            assertTrue(set.add(task));
        }
    }

    private LinkedList<AsyncTaskQueue.BaseTask> getNormalTask10() {
        LinkedList<AsyncTaskQueue.BaseTask> list = new LinkedList<>();

        for (int i = 0; i < 10; i++) {
            list.add(new NormalTask());
        }

        return list;
    }

    private LinkedList<AsyncTaskQueue.BaseTask> getErrorTask2() {
        LinkedList<AsyncTaskQueue.BaseTask> errorList = new LinkedList<>();

        errorList.add(new ErrorTask());
        errorList.add(new ErrorTaskNpt());

        return errorList;
    }

    class ErrorTask extends AsyncTaskQueue.BaseTask {

        @Override
        public void run() {
            SystemClock.sleep(new Random().nextInt(10) + 5);
            int x = 100 / 0;
            Logger.i(" result = " + x);
            throw new IllegalStateException("this is a test exception");
        }
    }

    class ErrorTaskNpt extends AsyncTaskQueue.BaseTask {

        @Override
        public void run() {
            SystemClock.sleep(new Random().nextInt(10) + 5);
            throw new NullPointerException(" this is a test exception");
        }
    }

    class NormalTask extends AsyncTaskQueue.BaseTask {

        byte[] data = new byte[1024 * 100];

        @Override
        public void run() {
            SystemClock.sleep(new Random().nextInt(30) + 10);
        }
    }

    public void testNofiy() {

        final Thread t = Thread.currentThread();
        final AtomicInteger index = new AtomicInteger(0);

        Observable.just(1, 2)
                .delay(2, TimeUnit.SECONDS)
                .subscribeOn(Schedulers.io())
                .subscribe(i -> {
                    synchronized (mTestCase) {
                        switch (i) {
                            case 1:
                                mTestCase.notify();
                                break;
                            case 2:
                                t.interrupt();
                                break;
                        }

                    }
                });

        while (!Thread.currentThread().isInterrupted()) {
            synchronized (mTestCase) {
                try {
                    wait();
                    index.addAndGet(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    index.addAndGet(1);
                    Thread.currentThread().interrupt();
                }
            }
        }

        assertTrue(index.get() == 2);

    }
}
