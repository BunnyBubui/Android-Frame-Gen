import com.universalframegen.interpolation.NativeInterpolator;
import java.util.Arrays;

public class Run {
    public static void main(String[] args) {
        var interp = new NativeInterpolator();
        int[] a = new int[64*48], b = new int[64*48];
        Arrays.fill(a, 0xff102030);
        Arrays.fill(b, 0xff102030);
        var mid = interp.interpolateNative(a, b, 64, 48, 0.5f, 8, 8);
        if (!Arrays.equals(a, mid)) throw new AssertionError("JNI identity mismatch");
        b[10*64+10] = 0xffbbccdd;
        var exact = interp.interpolateNative(a,b,64,48,1f,8,8);
        if (!Arrays.equals(exact,b)) throw new AssertionError("JNI endpoint mismatch");
        boolean bad = false;
        try { interp.interpolateNative(a,b,-1,48,.5f,8,8); }
        catch (IllegalArgumentException expected) { bad = true; }
        if (!bad) throw new AssertionError("JNI validation not propagated");
        System.out.println("PASS: Java→JNI native library, endpoints and input validation");
    }
}
