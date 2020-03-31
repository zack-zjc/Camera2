package com.zack.camera

import java.util.concurrent.*
import java.util.concurrent.locks.AbstractQueuedSynchronizer

/**
 * @Author zack
 * @Date 2020/3/27
 * @Description 用于异步获取对象处理
 * @Version 1.0
 */
class CameraFuture<T> : Future<T>{

    private val cameraAQS = CameraAQS<T>()

    fun set(value: T?): Boolean {
        return cameraAQS.set(value)
    }

    fun reset() {
        cameraAQS.resetState()
    }

    override fun isDone(): Boolean = cameraAQS.isDone()

    override fun get(): T? = cameraAQS.get()

    override fun get(timeout: Long, unit: TimeUnit): T? = cameraAQS[unit.toNanos(timeout)]

    override fun cancel(mayInterruptIfRunning: Boolean): Boolean = cameraAQS.cancel(mayInterruptIfRunning)

    override fun isCancelled(): Boolean = cameraAQS.isCancelled()

}

private class CameraAQS<T> : AbstractQueuedSynchronizer() {

    companion object {
        private const val RUNNING: Int = 0
        private const val COMPLETING: Int = 1
        private const val COMPLETED: Int = 2
        private const val CANCELLED: Int = 4
        private const val INTERRUPTED: Int = 8
    }

    private var value: T? = null

    private var exception: Throwable? = null

    /**
     * 重置状态
     */
    fun resetState(){
        tryReleaseShared(RUNNING)
        value = null
    }

    /**
     * 当前操作是否完成
     */
    fun isDone(): Boolean {
        return state and (COMPLETED or CANCELLED or INTERRUPTED) != 0
    }

    /**
     * 当前是否取消操作
     */
    fun isCancelled(): Boolean {
        return state and (CANCELLED or INTERRUPTED) != 0
    }

    /**
     * 根据操作结果返回
     */
    override fun tryAcquireShared(arg: Int): Int {
        return if (isDone()) 1 else -1
    }

    /**
     * 直接设置释放值并返回成功
     */
    override fun tryReleaseShared(finalState: Int): Boolean {
        state = finalState
        return true
    }

    /**
     * 中断等待时间返回
     */
    operator fun get(nanos: Long): T? {
        if (!tryAcquireSharedNanos(-1, nanos)) {
            throw TimeoutException("Timeout waiting for task.")
        }
        return getValue()
    }

    /**
     * 中断线程等待值返回
     */
    fun get(): T? {
        // Acquire the shared lock allowing interruption.
        acquireSharedInterruptibly(-1)
        return getValue()
    }

    /**
     * 根据状态返回值否则报错
     */
    private fun getValue(): T? {
        when (state) {
            COMPLETED -> return if (exception != null) {
                throw ExecutionException(exception)
            } else {
                value
            }
            CANCELLED, INTERRUPTED -> throw CancellationException("Task was cancelled.")
            else -> throw IllegalStateException("Error, synchronizer in invalid state: $state")
        }
    }

    /**
     * 是否中断
     */
    fun wasInterrupted(): Boolean {
        return state == INTERRUPTED
    }

    /**
     * 设定值并返回
     */
    fun set(v: T?): Boolean {
        return complete(v, null, COMPLETED)
    }

    /**
     * 设置中断并返回
     */
    fun setException(t: Throwable): Boolean {
        return complete(null, t, COMPLETED)
    }

    /**
     * 取消并中断操作
     */
    fun cancel(interrupt: Boolean): Boolean {
        return complete(null, null, if (interrupt) INTERRUPTED else CANCELLED)
    }

    /**
     * 修改运行状态并设值，如果状态不对则返回或报错
     */
    private fun complete(v: T?, t: Throwable?, finalState: Int): Boolean {
        val doCompletion = compareAndSetState(RUNNING, COMPLETING)
        if (doCompletion) {
            // If this thread successfully transitioned to COMPLETING, set the value
            // and exception and then release to the final state.
            this.value = v
            // Don't actually construct a CancellationException until necessary.
            this.exception = if (finalState and (CANCELLED or INTERRUPTED) != 0) {
                CancellationException("Future.cancel() was called.")
            } else {
                t
            }
            releaseShared(finalState)
        } else if (state == COMPLETING) {
            // If some other thread is currently completing the future, block until
            // they are done so we can guarantee completion.
            acquireShared(-1)
        }
        return doCompletion
    }
}