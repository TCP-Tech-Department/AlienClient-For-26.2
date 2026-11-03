package dev.luminous.mod.modules.impl.render;

import dev.luminous.Alien;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.render.ColorUtil;
import dev.luminous.api.utils.render.Render3DUtil;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.ColorSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.world.level.block.Blocks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.Direction;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

public class HoleESP extends Module {

    public static HoleESP INSTANCE;
    public final SliderSetting startFade = add(new SliderSetting("StartFade", 5f, 1f, 20f));
    public final SliderSetting distance = add(new SliderSetting("Distance", 6f, 1f, 20f));
    public final SliderSetting airHeight = add(new SliderSetting("AirHeight", 1, -3, 3, .01));
    public final BooleanSetting airYCheck = add(new BooleanSetting("AirYCheck", true));
    public final SliderSetting height = add(new SliderSetting("Height", 1, -3, 3, .1));
    public final SliderSetting wallHeight = add(new SliderSetting("WallHeight", 3, -3, 3, .1));
    public final BooleanSetting sideCheck = add(new BooleanSetting("SideCheck", true));
    private final ColorSetting airFill = add(new ColorSetting("AirFill", new Color(148, 0, 0, 100)).injectBoolean(true));
    private final ColorSetting airBox = add(new ColorSetting("AirBox", new Color(148, 0, 0, 100)).injectBoolean(true));
    private final ColorSetting airFade = add(new ColorSetting("AirFade", new Color(148, 0, 0, 0)).injectBoolean(true));
    private final ColorSetting normalFill = add(new ColorSetting("UnsafeFill", new Color(255, 0, 0, 50)).injectBoolean(true));
    private final ColorSetting normalBox = add(new ColorSetting("UnsafeBox", new Color(255, 0, 0, 100)).injectBoolean(true));
    private final ColorSetting normalFade = add(new ColorSetting("UnsafeFade", new Color(255, 0, 0, 0)).injectBoolean(true));
    private final ColorSetting bedrockFill = add(new ColorSetting("SafeFill", new Color(8, 255, 79, 50)).injectBoolean(true));
    private final ColorSetting bedrockBox = add(new ColorSetting("SafeBox", new Color(8, 255, 79, 100)).injectBoolean(true));
    private final ColorSetting bedrockFade = add(new ColorSetting("SafeFade", new Color(8, 255, 79, 100)).injectBoolean(true));
    private final ColorSetting wallFill = add(new ColorSetting("WallFill", new Color(0, 255, 255, 128)).injectBoolean(true));
    private final ColorSetting wallBox = add(new ColorSetting("WallBox", new Color(0, 225, 255, 255)).injectBoolean(true));
    private final ColorSetting wallFade = add(new ColorSetting("WallFade", new Color(0, 255, 255, 64)).injectBoolean(true));
    private final ColorSetting wallSideFill = add(new ColorSetting("WallSideFill", new Color(0, 255, 255, 128)).injectBoolean(true));
    private final ColorSetting wallSideBox = add(new ColorSetting("WallSideBox", new Color(0, 225, 255, 255)).injectBoolean(true));
    private final ColorSetting wallSideFade = add(new ColorSetting("WallSideFade", new Color(0, 255, 255, 64)).injectBoolean(true));
    private final SliderSetting updateDelay =
            add(new SliderSetting("UpdateDelay", 50, 0, 1000));
    private final List<BlockPos> tempNormalList = new ArrayList<>();
    private final List<BlockPos> tempBedrockList = new ArrayList<>();
    private final List<BlockPos> tempAirList = new ArrayList<>();
    private final List<BlockPos> tempWallList = new ArrayList<>();
    private final List<BlockPos> tempWallSideList = new ArrayList<>();
    private final Timer timer = new Timer();
    boolean drawing = false;
    private List<BlockPos> normalList = new ArrayList<>();
    private List<BlockPos> bedrockList = new ArrayList<>();
    private List<BlockPos> airList = new ArrayList<>();
    private List<BlockPos> wallList = new ArrayList<>();
    private List<BlockPos> wallSideList = new ArrayList<>();

    public HoleESP() {
        super("HoleESP", Category.Render);
        setChinese("坑透视");
        INSTANCE = this;
    }

    public void onThread() {
        if (nullCheck() || isOff()) return;
        if (!drawing && timer.passedMs(updateDelay.getValue())) {
            normalList = new ArrayList<>(tempNormalList);
            bedrockList = new ArrayList<>(tempBedrockList);
            airList = new ArrayList<>(tempAirList);
            wallList = new ArrayList<>(tempWallList);
            wallSideList = new ArrayList<>(tempWallSideList);
            timer.reset();
            tempBedrockList.clear();
            tempNormalList.clear();
            tempAirList.clear();
            tempWallList.clear();
            tempWallSideList.clear();
            for (BlockPos pos : BlockUtil.getSphere(distance.getValueFloat(), mc.player.position())) {
                Direction side;
                if (isBedrock(pos) && isBedrock(pos.above(2)) && isBedrock(pos.below())) {
                    side = getWallSide(pos);
                    if (side != null || !sideCheck.getValue())
                        tempWallList.add(pos);
                    if (side != null)
                        tempWallSideList.add(pos.relative(side));
                }
                Type type = isHole(pos);
                if (type == Type.Bedrock) {
                    tempBedrockList.add(pos);
                } else if (type == Type.Normal) {
                    tempNormalList.add(pos);
                } else if (type == Type.Air) {
                    tempAirList.add(pos);
                }
            }
        }
    }

    private Direction getWallSide(BlockPos pos) {
        double distance = Double.MAX_VALUE;
        Direction side = null;
        for (Direction direction : Direction.values()) {
            if (direction == Direction.UP || direction == Direction.DOWN) continue;
            BlockPos offsetPos = pos.relative(direction);
            if (!BlockUtil.canCollide(new AABB(offsetPos.below()))) continue;
            if (BlockUtil.canCollide(new AABB(offsetPos))) continue;
            if (BlockUtil.canCollide(new AABB(offsetPos.above()))) continue;

            if (side == null) {
                side = direction;
                distance = mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(offsetPos));
            } else if (mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(offsetPos)) < distance) {
                side = direction;
                distance = mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(offsetPos));
            }
        }
        return side;
    }

    private boolean isBedrock(BlockPos pos) {
        return mc.level.getBlockState(pos).getBlock() == Blocks.BEDROCK;
    }

    Type isHole(BlockPos pos) {
        // 26.2: Level 无 isAir(pos)，改用 BlockState.isAir()
        if (mc.level.getBlockState(pos).isAir() && (!airYCheck.getValue() || pos.getY() == mc.player.getBlockY() - 1 || pos.getY() == mc.player.getBlockY()) && Alien.HOLE.isHard(pos.above()))
            return Type.Air;
        int blockProgress = 0;
        boolean bedRock = true;
        for (Direction i : Direction.values()) {
            if (i == Direction.UP || i == Direction.DOWN) continue;
            if (Alien.HOLE.isHard(pos.relative(i))) {
                if (mc.level.getBlockState(pos.relative(i)).getBlock() != Blocks.BEDROCK) {
                    bedRock = false;
                }
                blockProgress++;
            }
        }
        if ((mc.level.getBlockState(pos).isAir() && mc.level.getBlockState(pos.above()).isAir() && mc.level.getBlockState(pos.above(2)).isAir()) && blockProgress > 3 && BlockUtil.canCollide(mc.player, new AABB(pos.below()))) {
            if (bedRock) return Type.Bedrock;
            return Type.Normal;
        }
        if (Alien.HOLE.isDoubleHole(pos)) return Type.Normal;
        return Type.None;
    }

    @Override
    public void onRender3D(PoseStack matrixStack) {
        drawing = true;
        draw(matrixStack, bedrockList, bedrockFill, bedrockFade, bedrockBox, height.getValue());
        draw(matrixStack, airList, airFill, airFade, airBox, airHeight.getValue());
        draw(matrixStack, normalList, normalFill, normalFade, normalBox, height.getValue());
        draw(matrixStack, wallList, wallFill, wallFade, wallBox, wallHeight.getValue());
        draw(matrixStack, wallSideList, wallSideFill, wallSideFade, wallSideBox, height.getValue());
        drawing = false;
    }

    private void draw(PoseStack matrixStack, List<BlockPos> list, ColorSetting fill, ColorSetting fade, ColorSetting box, double height) {
        for (BlockPos pos : list) {
            double distance = mc.player.position().distanceTo(Vec3.atCenterOf(pos));
            double alpha = distance > startFade.getValue() ? Math.max(Math.min(1, (1 - ((distance - startFade.getValue()) / (this.distance.getValue() - startFade.getValue())))), 0) : 1;
            AABB espBox = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + height, pos.getZ() + 1);
            if (fill.booleanValue) {
                if (fade.booleanValue) {
                    Render3DUtil.drawFadeFill(espBox, ColorUtil.injectAlpha(fill.getValue(), (int) (fill.getValue().getAlpha() * alpha)), ColorUtil.injectAlpha(fade.getValue(), (int) (fade.getValue().getAlpha() * alpha)));
                } else {
                    Render3DUtil.drawFill(espBox, ColorUtil.injectAlpha(fill.getValue(), (int) (fill.getValue().getAlpha() * alpha)));
                }
            }
            if (box.booleanValue) {
                Render3DUtil.drawBox(espBox, ColorUtil.injectAlpha(box.getValue(), (int) (box.getValue().getAlpha() * alpha)));
            }
        }
    }

    public enum Type {
        None,
        Air,
        Normal,
        Bedrock
    }
}
