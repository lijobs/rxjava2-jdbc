package org.davidmoten.rx.jdbc.pool;

import java.sql.Connection;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.DataSource;

import org.davidmoten.rx.jdbc.ConnectionProvider;
import org.davidmoten.rx.jdbc.Util;
import org.davidmoten.rx.pool.Member2;
import org.davidmoten.rx.pool.NonBlockingPool2;
import org.davidmoten.rx.pool.Pool2;

import com.github.davidmoten.guavamini.Preconditions;

import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Predicate;
import io.reactivex.schedulers.Schedulers;

public final class NonBlockingConnectionPool2 implements Pool2<Connection> {

    private final AtomicReference<NonBlockingPool2<Connection>> pool = new AtomicReference<>();

    public NonBlockingConnectionPool2(
            org.davidmoten.rx.pool.NonBlockingPool2.Builder<Connection> builder) {
        pool.set(builder.build());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private ConnectionProvider cp;
        private Predicate<Connection> healthy = c -> true;
        private int maxPoolSize = 5;
        private long returnToPoolDelayAfterHealthCheckFailureMs = 1000;
        private long idleTimeBeforeHealthCheckMs = 60000;
        private long maxIdleTimeMs = 30 * 60000;
        private long checkoutRetryIntervalMs = 30000;
        private Consumer<Connection> disposer = Util::closeSilently;
        private Scheduler scheduler = null;

        public Builder connectionProvider(ConnectionProvider cp) {
            this.cp = cp;
            return this;
        }

        public Builder connectionProvider(DataSource d) {
            this.cp = Util.connectionProvider(d);
            return this;
        }

        public Builder url(String url) {
            return connectionProvider(Util.connectionProvider(url));
        }

        public Builder maxIdleTimeMs(long value) {
            this.maxIdleTimeMs = value;
            return this;
        }

        public Builder maxIdleTime(long value, TimeUnit unit) {
            return maxIdleTimeMs(unit.toMillis(value));
        }

        public Builder idleTimeBeforeHealthCheckMs(long value) {
            Preconditions.checkArgument(value >= 0);
            this.idleTimeBeforeHealthCheckMs = value;
            return this;
        }

        public Builder checkoutRetryIntervalMs(long value) {
            this.checkoutRetryIntervalMs = value;
            return this;
        }

        public Builder checkoutRetryInterval(long value, TimeUnit unit) {
            return checkoutRetryIntervalMs(unit.toMillis(value));
        }

        public Builder idleTimeBeforeHealthCheck(long value, TimeUnit unit) {
            return idleTimeBeforeHealthCheckMs(unit.toMillis(value));
        }

        public Builder healthy(Predicate<Connection> healthy) {
            this.healthy = healthy;
            return this;
        }

        /**
         * Sets the maximum connection pool size. Default is 5.
         * 
         * @param maxPoolSize
         *            maximum number of connections in the pool
         * @return this
         */
        public Builder maxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
            return this;
        }

        public Builder returnToPoolDelayAfterHealthCheckFailureMs(long value) {
            this.returnToPoolDelayAfterHealthCheckFailureMs = value;
            return this;
        }

        public Builder returnToPoolDelayAfterHealthCheckFailure(long value, TimeUnit unit) {
            return returnToPoolDelayAfterHealthCheckFailureMs(unit.toMillis(value));
        }

        /**
         * Sets the scheduler used for emitting connections (must be scheduled to
         * another thread to break the chain of stack calls otherwise can get
         * StackOverflowError) and for scheduling timeouts and retries. Defaults to
         * {@code Schedulers.from(Executors.newFixedThreadPool(maxPoolSize))}. Do not
         * set the scheduler to {@code Schedulers.trampoline()} because queries will
         * block waiting for timeout workers. Also, do not use a single-threaded
         * {@link Scheduler} because you may encounter {@link StackOverflowError}.
         * 
         * @param scheduler
         *            scheduler to use for emitting connections and for scheduling
         *            timeouts and retries. Defaults to
         *            {@code Schedulers.from(Executors.newFixedThreadPool(maxPoolSize))}.
         *            Do not use {@code Schedulers.trampoline()}.
         * @return this
         */
        public Builder scheduler(Scheduler scheduler) {
            Preconditions.checkArgument(scheduler != Schedulers.trampoline(),
                    "do not use trampoline scheduler because of risk of stack overflow");
            this.scheduler = scheduler;
            return this;
        }

        public NonBlockingConnectionPool2 build() {
            if (scheduler == null) {
                scheduler = Schedulers.from(Executors.newFixedThreadPool(maxPoolSize));
            }
            return new NonBlockingConnectionPool2(NonBlockingPool2 //
                    .factory(() -> cp.get()) //
                    .idleTimeBeforeHealthCheckMs(idleTimeBeforeHealthCheckMs) //
                    .maxIdleTimeMs(maxIdleTimeMs) //
                    .checkoutRetryIntervalMs(checkoutRetryIntervalMs) //
                    .scheduler(scheduler) //
                    .disposer(disposer)//
                    .healthy(healthy) //
                    .scheduler(scheduler) //
                    .maxSize(maxPoolSize) //
                    .returnToPoolDelayAfterHealthCheckFailureMs(
                            returnToPoolDelayAfterHealthCheckFailureMs)); //
        }

    }

    @Override
    public Single<Member2<Connection>> member() {
        return pool.get().member();
    }

    @Override
    public void close() {
        pool.get().close();
    }

}
