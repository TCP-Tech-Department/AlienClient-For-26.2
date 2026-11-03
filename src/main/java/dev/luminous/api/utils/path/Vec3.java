package dev.luminous.api.utils.path;

public record Vec3(double x, double y, double z) {

    public Vec3 addVector(double x, double y, double z) {
        return new Vec3(this.x + x, this.y + y, this.z + z);
    }

    public Vec3 floor() {
        return new Vec3(Math.floor(this.x), Math.floor(this.y), Math.floor(this.z));
    }

    public double squareDistanceTo(Vec3 v) {
        return Math.pow(v.x - this.x, 2.0) + Math.pow(v.y - this.y, 2.0) + Math.pow(v.z - this.z, 2.0);
    }

    public Vec3 add(Vec3 v) {
        return this.addVector(v.x(), v.y(), v.z());
    }

    public net.minecraft.world.phys.Vec3 mc() {
        return new net.minecraft.world.phys.Vec3(this.x, this.y, this.z);
    }

    @Override
    public String toString() {
        return "[" + this.x + ";" + this.y + ";" + this.z + "]";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Vec3(double x1, double y1, double z1)) {
            return this.x == x1 && this.y == y1 && this.z == z1;
        }
        return false;
    }
}
