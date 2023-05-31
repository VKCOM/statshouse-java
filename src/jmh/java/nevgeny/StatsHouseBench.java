package nevgeny;

import com.github.nevgeny.StatsHouse;
import org.openjdk.jmh.annotations.*;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

@BenchmarkMode({Mode.AverageTime})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(1)
public class StatsHouseBench {


    @State(Scope.Thread)
    public static class ShState{
        StatsHouse sh;
        StatsHouse.Metric m;
        {
            try {
                sh = new StatsHouse( InetAddress.getByName("127.0.0.1"), 65535, "dev");
            } catch (SocketException e) {
                throw new RuntimeException(e);
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }
            m = sh.metric("test_jv");
            m = m.withTag("get");
            m = m.withTag("test");
        }

        @TearDown(Level.Trial)
        public void tearDown(){
            System.out.println("gc count of " + getGcCount());
        }

    }
    @Benchmark
    public void InitAndCount(ShState sh) {
        var metric = sh.sh.metric("test_jv");
        metric = metric.withTag("get");
        metric = metric.withTag("test");
        metric.count(1);
    }

    @Benchmark
    public void Count(ShState sh) {
        sh.m.count(1);
    }

    static long getGcCount() {
        long sum = 0;
        for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) {
            long count = b.getCollectionCount();
            if (count != -1) { sum +=  count; }
        }
        return sum;
    }
}
