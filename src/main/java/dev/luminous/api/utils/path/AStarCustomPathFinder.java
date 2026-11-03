package dev.luminous.api.utils.path;

import dev.luminous.api.utils.Wrapper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;

import java.util.ArrayList;
import java.util.Comparator;

public class AStarCustomPathFinder implements Wrapper {
    private static final Vec3[] flatCardinalDirections = new Vec3[]{
            new Vec3(1.0, 0.0, 0.0), new Vec3(-1.0, 0.0, 0.0), new Vec3(0.0, 0.0, 1.0), new Vec3(0.0, 0.0, -1.0)
    };
    private final Vec3 startVec3;
    private final Vec3 endVec3;
    private final ArrayList<Hub> hubs = new ArrayList<>();
    private final ArrayList<Hub> hubsToWork = new ArrayList<>();
    private ArrayList<Vec3> path = new ArrayList<>();

    public AStarCustomPathFinder(Vec3 startVec3, Vec3 endVec3) {
        this.startVec3 = startVec3.addVector(0.0, 0.0, 0.0).floor();
        this.endVec3 = endVec3.addVector(0.0, 0.0, 0.0).floor();
    }

    public static boolean checkPositionValidity(Vec3 loc, boolean checkGround) {
        return checkPositionValidity((int) loc.x(), (int) loc.y(), (int) loc.z(), checkGround);
    }

    public static boolean checkPositionValidity(int x, int y, int z, boolean checkGround) {
        BlockPos block1 = new BlockPos(x, y, z);
        BlockPos block2 = new BlockPos(x, y + 1, z);
        BlockPos block3 = new BlockPos(x, y - 1, z);
        return !isBlockSolid(block1) && !isBlockSolid(block2) && (isBlockSolid(block3) || !checkGround) && isSafeToWalkOn(block3);
    }

    private static boolean isBlockSolid(BlockPos block) {
        return mc.level.getBlockState(block).isCollisionShapeFullBlock(mc.level, block)
                || mc.level.getBlockState(block).getBlock() instanceof SlabBlock
                || mc.level.getBlockState(block).getBlock() instanceof StairBlock
                || mc.level.getBlockState(block).getBlock() instanceof CactusBlock
                || mc.level.getBlockState(block).getBlock() instanceof ChestBlock
                || mc.level.getBlockState(block).getBlock() instanceof EnderChestBlock
                || mc.level.getBlockState(block).getBlock() instanceof SkullBlock
                || mc.level.getBlockState(block).getBlock() instanceof IronBarsBlock
                || mc.level.getBlockState(block).getBlock() instanceof FenceBlock
                || mc.level.getBlockState(block).getBlock() instanceof WallBlock
                || mc.level.getBlockState(block).getBlock() instanceof StainedGlassBlock
                || mc.level.getBlockState(block).getBlock() instanceof PistonBaseBlock
                || mc.level.getBlockState(block).getBlock() instanceof MovingPistonBlock
                || mc.level.getBlockState(block).getBlock() instanceof PistonHeadBlock
                || mc.level.getBlockState(block).getBlock() instanceof StainedGlassBlock
                || mc.level.getBlockState(block).getBlock() instanceof TrapDoorBlock;
    }

    private static boolean isSafeToWalkOn(BlockPos block) {
        return !(mc.level.getBlockState(block).getBlock() instanceof FenceBlock)
                && !(mc.level.getBlockState(block).getBlock() instanceof WallBlock);
    }

    public ArrayList<Vec3> getPath() {
        return this.path;
    }

    public void compute() {
        this.compute(1000, 4);
    }

    public void compute(int loops, int depth) {
        this.path.clear();
        this.hubsToWork.clear();
        ArrayList<Vec3> initPath = new ArrayList<>();
        initPath.add(this.startVec3);
        this.hubsToWork.add(new Hub(this.startVec3, null, initPath, this.startVec3.squareDistanceTo(this.endVec3), 0.0, 0.0));

        label51:
        for (int i = 0; i < loops; i++) {
            this.hubsToWork.sort(new CompareHub());
            int j = 0;
            if (this.hubsToWork.isEmpty()) {
                break;
            }

            for (Hub o : new ArrayList<>(this.hubsToWork)) {
                if (++j <= depth) {
                    this.hubsToWork.remove(o);
                    this.hubs.add(o);

                    for (Vec3 direction : flatCardinalDirections) {
                        Vec3 loc = o.getLoc().add(direction).floor();
                        if (checkPositionValidity(loc, false) && this.addHub(o, loc, 0.0)) {
                            break label51;
                        }
                    }

                    Vec3 loc1 = o.getLoc().addVector(0.0, 1.0, 0.0).floor();
                    if (checkPositionValidity(loc1, false) && this.addHub(o, loc1, 0.0)) {
                        break label51;
                    }

                    Vec3 loc2 = o.getLoc().addVector(0.0, -1.0, 0.0).floor();
                    if (checkPositionValidity(loc2, false) && this.addHub(o, loc2, 0.0)) {
                        break label51;
                    }
                }
            }
        }

        this.hubs.sort(new CompareHub());
        this.path = this.hubs.getFirst().getPath();
    }

    public Hub isHubExisting(Vec3 loc) {
        for (Hub hub : this.hubs) {
            if (hub.getLoc().x() == loc.x() && hub.getLoc().y() == loc.y() && hub.getLoc().z() == loc.z()) {
                return hub;
            }
        }

        for (Hub hub : this.hubsToWork) {
            if (hub.getLoc().x() == loc.x() && hub.getLoc().y() == loc.y() && hub.getLoc().z() == loc.z()) {
                return hub;
            }
        }

        return null;
    }

    public boolean addHub(Hub parent, Vec3 loc, double cost) {
        Hub existingHub = this.isHubExisting(loc);
        double totalCost = cost;
        if (parent != null) {
            totalCost = cost + parent.getTotalCost();
        }

        if (existingHub == null) {
            double minDistanceSquared = 9;
            if (loc.x() == this.endVec3.x() && loc.y() == this.endVec3.y() && loc.z() == this.endVec3.z()
                    || loc.squareDistanceTo(this.endVec3) <= minDistanceSquared) {
                this.path.clear();
                this.path = parent.getPath();
                this.path.add(loc);
                return true;
            }

            ArrayList<Vec3> path = new ArrayList<>(parent.getPath());
            path.add(loc);
            this.hubsToWork.add(new Hub(loc, parent, path, loc.squareDistanceTo(this.endVec3), cost, totalCost));
        } else if (existingHub.getCost() > cost) {
            ArrayList<Vec3> path = new ArrayList<>(parent.getPath());
            path.add(loc);
            existingHub.setLoc(loc);
            existingHub.setParent(parent);
            existingHub.setPath(path);
            existingHub.setSquareDistanceToFromTarget(loc.squareDistanceTo(this.endVec3));
            existingHub.setCost(cost);
            existingHub.setTotalCost(totalCost);
        }

        return false;
    }

    public static class CompareHub implements Comparator<Hub> {
        public int compare(Hub o1, Hub o2) {
            return (int) (o1.getSquareDistanceToFromTarget() + o1.getTotalCost() - (o2.getSquareDistanceToFromTarget() + o2.getTotalCost()));
        }
    }

    public static class Hub {
        private Vec3 loc;
        private Hub parent;
        private ArrayList<Vec3> path;
        private double squareDistanceToFromTarget;
        private double cost;
        private double totalCost;

        public Hub(Vec3 loc, Hub parent, ArrayList<Vec3> path, double squareDistanceToFromTarget, double cost, double totalCost) {
            this.loc = loc;
            this.parent = parent;
            this.path = path;
            this.squareDistanceToFromTarget = squareDistanceToFromTarget;
            this.cost = cost;
            this.totalCost = totalCost;
        }

        public Vec3 getLoc() {
            return this.loc;
        }

        public void setLoc(Vec3 loc) {
            this.loc = loc;
        }

        public Hub getParent() {
            return this.parent;
        }

        public void setParent(Hub parent) {
            this.parent = parent;
        }

        public ArrayList<Vec3> getPath() {
            return this.path;
        }

        public void setPath(ArrayList<Vec3> path) {
            this.path = path;
        }

        public double getSquareDistanceToFromTarget() {
            return this.squareDistanceToFromTarget;
        }

        public void setSquareDistanceToFromTarget(double squareDistanceToFromTarget) {
            this.squareDistanceToFromTarget = squareDistanceToFromTarget;
        }

        public double getCost() {
            return this.cost;
        }

        public void setCost(double cost) {
            this.cost = cost;
        }

        public double getTotalCost() {
            return this.totalCost;
        }

        public void setTotalCost(double totalCost) {
            this.totalCost = totalCost;
        }
    }
}
