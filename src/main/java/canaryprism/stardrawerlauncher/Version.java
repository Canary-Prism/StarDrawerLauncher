package canaryprism.stardrawerlauncher;

public class Version implements Comparable<Version> {
    int major, minor, patch;

    public Version(int major, int minor, int patch) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    public Version(String version) {
        var split = version.split("\\.");
        this.major = Integer.parseInt(split[0]);
        this.minor = Integer.parseInt(split[1]);
        this.patch = Integer.parseInt(split[2]);
    }

    @Override
    public int compareTo(Version other) {
        if (this.major != other.major) {
            return this.major - other.major;
        }
        if (this.minor != other.minor) {
            return this.minor - other.minor;
        }
        return this.patch - other.patch;
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
