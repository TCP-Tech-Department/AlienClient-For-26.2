package dev.luminous.mod.modules.impl.misc;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.render.Render3DUtil;
import dev.luminous.api.utils.render.TextUtil;
import dev.luminous.api.utils.world.BlockPosX;
import dev.luminous.core.impl.CommandManager;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BindSetting;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.ColorSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.mojang.blaze3d.vertex.PoseStack;
// 26.2：SoundCategory → SoundSource
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.concurrent.ConcurrentHashMap;


public class Punctuation extends Module {


    private final BooleanSetting sound =
            add(new BooleanSetting("Sound", true));


    private final SliderSetting clearTime =
            add(new SliderSetting(
                    "ClearTime",
                    10,
                    0,
                    100,
                    0.1
            ).setSuffix("s"));



    private final ColorSetting color =
            add(new ColorSetting(
                    "Color",
                    new Color(255,255,255,100)
            ));



    private final BindSetting enemySpot =
            add(new BindSetting(
                    "EnemySpot",
                    -1
            ));



    private final ConcurrentHashMap<String, Spot> waypoint =
            new ConcurrentHashMap<>();


    private boolean pressed = false;



    public Punctuation() {

        super(
                "Punctuation",
                Category.Misc
        );

        setChinese("标点");

    }




    @Override
    public void onDisable() {

        waypoint.clear();

    }





    @EventListener
    public void onUpdate(UpdateEvent event) {


        waypoint.values()
                .removeIf(
                        t -> t.timer.passedS(clearTime.getValue())
                );



        /*
            已删除：

            EnemyHere协议

            AES坐标加密

            Base64广播

            sendChatMessage坐标上传


            现在只进行本地标记
        */


        if(enemySpot.isPressed()
                && mc.gui.screen() == null){


            if(!pressed){


                HitResult hitResult =
                        // 26.2：Entity#raycast → Entity#pick(range, tickDelta, withLiquids)
                        mc.getCameraEntity()
                                .pick(
                                        256,
                                        0,
                                        false
                                );



                if(hitResult instanceof BlockHitResult blockHitResult){


                    BlockPos pos =
                            // 26.2：BlockHitResult#blockPosition → getBlockPos
                            blockHitResult.getBlockPos();



                    String name =
                            "Local";


                    waypoint.put(
                            name,
                            new Spot(
                                    name,
                                    new BlockPosX(
                                            pos.getX(),
                                            pos.getY(),
                                            pos.getZ()
                                    ),
                                    color.getValue(),
                                    new Timer()
                            )
                    );



                    if(sound.getValue()){

                        mc.level.playSound(
                                mc.player,
                                mc.player.blockPosition(),
                                SoundEvents.EXPERIENCE_ORB_PICKUP,
                                SoundSource.PLAYERS,
                                100f,
                                1.9f
                        );

                    }



                    CommandManager.sendMessage(
                            "Marked at §r("
                                    + pos.getX()
                                    + ", "
                                    + pos.getY()
                                    + ", "
                                    + pos.getZ()
                                    + ")"
                    );

                }

            }


            pressed=true;


        }else{


            pressed=false;

        }

    }





    @Override
    public void onRender2D(
            GuiGraphicsExtractor context,
            float tickDelta
    ){


        for(Spot spot : waypoint.values()){


            Vec3 vector =
                    TextUtil.worldSpaceToScreenSpace(
                            Vec3.atCenterOf(spot.pos)
                                    .add(0,1,0)
                    );



            String text =
                    "§a"
                            + spot.name
                            + " §f("
                            + spot.pos.getX()
                            + ", "
                            + spot.pos.getY()
                            + ", "
                            + spot.pos.getZ()
                            + ")";



            if(vector.z > 0 && vector.z < 1){


                double posX =
                        vector.x;


                double posY =
                        vector.y;



                double endPosX =
                        Math.max(
                                vector.x,
                                vector.z
                        );



                float diff =
                        (float)
                                (endPosX-posX)
                                /2;



                float textWidth =
                        mc.font.width(text);



                float tagX =
                        (float)
                                ((posX+diff-textWidth/4));



                // 26.2：Matrix3x2fStack 只有 pushMatrix/popMatrix，scale 是 2D（无 z 参数）
                context.pose().pushMatrix();


                context.pose()
                        .scale(
                                0.5f,
                                0.5f
                        );



                context.text(
                        mc.font,
                        text,
                        (int)(tagX)*2,
                        (int)
                                ((posY-11
                                        +mc.font.lineHeight*1.2))
                                *2,
                        -1,
                        true
                );



                context.pose().popMatrix();

            }

        }

    }





    @Override
    public void onRender3D(
            PoseStack matrixStack
    ){


        for(Spot spot : waypoint.values()){


            Render3DUtil.drawFill(new AABB(
                            spot.pos.getX()+0.25,
                            -60,
                            spot.pos.getZ()+0.25,
                            spot.pos.getX()+0.75,
                            320,
                            spot.pos.getZ()+0.75
                    ),
                    spot.color
            );


        }

    }





    private record Spot(
            String name,
            BlockPos pos,
            Color color,
            Timer timer
    ){

    }

}