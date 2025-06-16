package zone.rong.loliasm.patches.devenv;

import net.minecraftforge.fml.common.versioning.ArtifactVersion;

public class LambdaHelper {
    // Called by the lambda, receives Object, returns boolean
    public static boolean predicateTest(Object o) {
        if (!(o instanceof ArtifactVersion)) return false;
        ArtifactVersion av = (ArtifactVersion) o;
        return "forge".equals(av.getLabel());
    }
}
