package me.andrew.gravitychanger.mixin;

import me.andrew.gravitychanger.accessor.PlayerEntityAccessor;
import me.andrew.gravitychanger.util.RotationUtil;
import net.minecraft.block.BlockState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundEvent;
import net.minecraft.tag.FluidTags;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {
    @Shadow public abstract void readCustomDataFromNbt(NbtCompound nbt);

    @Shadow public abstract EntityDimensions getDimensions(EntityPose pose);

    @Shadow public abstract boolean canMoveVoluntarily();

    @Shadow public abstract boolean hasStatusEffect(StatusEffect effect);

    @Shadow protected abstract boolean shouldSwimInFluids();

    @Shadow public abstract boolean canWalkOnFluid(Fluid fluid);

    @Shadow protected abstract float getBaseMovementSpeedMultiplier();

    @Shadow public abstract float getMovementSpeed();

    @Shadow public abstract boolean isClimbing();

    @Shadow public abstract Vec3d method_26317(double d, boolean bl, Vec3d vec3d);

    @Shadow public abstract boolean isFallFlying();

    @Shadow protected abstract SoundEvent getFallSound(int distance);

    @Shadow public abstract Vec3d method_26318(Vec3d vec3d, float f);

    @Shadow @Nullable public abstract StatusEffectInstance getStatusEffect(StatusEffect effect);

    @Shadow public abstract boolean hasNoDrag();

    @Shadow public abstract void updateLimbs(LivingEntity entity, boolean flutter);

    @Shadow public abstract float getYaw(float tickDelta);

    public LivingEntityMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @Inject(
            method = "onTrackedDataSet",
            at = @At("RETURN")
    )
    private void inject_onTrackedDataSet(TrackedData<?> data, CallbackInfo ci) {
        if(!((Object) this instanceof PlayerEntity)) return;
        ((PlayerEntityAccessor) this).gravitychanger$onTrackedData(data);
    }

    @Inject(
            method = "travel",
            at = @At("HEAD"),
            cancellable = true
    )
    private void inject_travel(Vec3d movementInput, CallbackInfo ci) {
        if(!((Object) this instanceof PlayerEntity)) return;
        PlayerEntityAccessor playerEntityAccessor = (PlayerEntityAccessor) this;
        Direction gravityDirection = playerEntityAccessor.gravitychanger$getGravityDirection();

        ci.cancel();

        if (this.canMoveVoluntarily() || this.isLogicalSideForUpdatingMovement()) {
            double gravity = 0.08D;
            boolean isMovingDown = RotationUtil.vecWorldToPlayer(this.getVelocity(), gravityDirection).y <= 0.0D;
            if (isMovingDown && this.hasStatusEffect(StatusEffects.SLOW_FALLING)) {
                gravity = 0.01D;
                this.fallDistance = 0.0F;
            }

            FluidState fluidState = this.world.getFluidState(this.getBlockPos());
            float movementSpeedMultiplier;
            double playerY;
            if (this.isTouchingWater() && this.shouldSwimInFluids() && !this.canWalkOnFluid(fluidState.getFluid())) {
                playerY = RotationUtil.vecWorldToPlayer(this.getPos(), gravityDirection).y;
                movementSpeedMultiplier = this.isSprinting() ? 0.9F : this.getBaseMovementSpeedMultiplier();
                float movementSpeed = 0.02F;
                float depthStriderMultiplier = (float) EnchantmentHelper.getDepthStrider((LivingEntity)(Object) this);
                if (depthStriderMultiplier > 3.0F) {
                    depthStriderMultiplier = 3.0F;
                }

                if (!this.onGround) {
                    depthStriderMultiplier *= 0.5F;
                }

                if (depthStriderMultiplier > 0.0F) {
                    movementSpeedMultiplier += (0.54600006F - movementSpeedMultiplier) * depthStriderMultiplier / 3.0F;
                    movementSpeed += (this.getMovementSpeed() - movementSpeed) * depthStriderMultiplier / 3.0F;
                }

                if (this.hasStatusEffect(StatusEffects.DOLPHINS_GRACE)) {
                    movementSpeedMultiplier = 0.96F;
                }

                this.updateVelocity(movementSpeed, movementInput);
                this.move(MovementType.SELF, this.getVelocity());
                Vec3d velocity = this.getVelocity();
                Vec3d playerVelocity = RotationUtil.vecWorldToPlayer(velocity, gravityDirection);
                if (this.horizontalCollision && this.isClimbing()) {
                    playerVelocity = new Vec3d(playerVelocity.x, 0.2D, playerVelocity.z);
                }

                this.setVelocity(RotationUtil.vecPlayerToWorld(playerVelocity.multiply(movementSpeedMultiplier, 0.800000011920929D, movementSpeedMultiplier), gravityDirection));
                Vec3d playerAdjustedVelocity = this.method_26317(gravity, isMovingDown, RotationUtil.vecWorldToPlayer(this.getVelocity(), gravityDirection));
                this.setVelocity(RotationUtil.vecPlayerToWorld(playerAdjustedVelocity, gravityDirection));
                Vec3d boxOffset = RotationUtil.vecPlayerToWorld(playerAdjustedVelocity.add(0.0D, 0.6000000238418579D - RotationUtil.vecWorldToPlayer(this.getPos(), gravityDirection).y + playerY, 0.0D), gravityDirection);
                if (this.horizontalCollision && this.doesNotCollide(boxOffset.x, boxOffset.y, boxOffset.z)) {
                    this.setVelocity(RotationUtil.vecPlayerToWorld(playerAdjustedVelocity.x, 0.30000001192092896D, playerAdjustedVelocity.z, gravityDirection));
                }
            } else if (this.isInLava() && this.shouldSwimInFluids() && !this.canWalkOnFluid(fluidState.getFluid())) {
                playerY = RotationUtil.vecWorldToPlayer(this.getPos(), gravityDirection).y;
                this.updateVelocity(0.02F, movementInput);
                this.move(MovementType.SELF, this.getVelocity());
                Vec3d playerVelocity;
                if (this.getFluidHeight(FluidTags.LAVA) <= this.getSwimHeight()) {
                    this.setVelocity(RotationUtil.vecPlayerToWorld(RotationUtil.vecWorldToPlayer(this.getVelocity(), gravityDirection).multiply(0.5D, 0.800000011920929D, 0.5D), gravityDirection));
                    playerVelocity = this.method_26317(gravity, isMovingDown, RotationUtil.vecWorldToPlayer(this.getVelocity(), gravityDirection));
                    this.setVelocity(RotationUtil.vecPlayerToWorld(playerVelocity, gravityDirection));
                } else {
                    this.setVelocity(this.getVelocity().multiply(0.5D));
                }

                if (!this.hasNoGravity()) {
                    this.setVelocity(RotationUtil.vecPlayerToWorld(RotationUtil.vecWorldToPlayer(this.getVelocity(), gravityDirection).add(0.0D, -gravity / 4.0D, 0.0D), gravityDirection));
                }

                playerVelocity = RotationUtil.vecWorldToPlayer(this.getVelocity(), gravityDirection);
                Vec3d boxOffset = RotationUtil.vecPlayerToWorld(playerVelocity.add(0.0D, 0.6000000238418579D - RotationUtil.vecWorldToPlayer(this.getPos(), gravityDirection).y + playerY, 0.0D), gravityDirection);
                if (this.horizontalCollision && this.doesNotCollide(boxOffset.x, boxOffset.y, boxOffset.z)) {
                    this.setVelocity(RotationUtil.vecPlayerToWorld(playerVelocity.x, 0.30000001192092896D, playerVelocity.z, gravityDirection));
                }
            } else if (this.isFallFlying()) {
                Vec3d playerVelocity = RotationUtil.vecWorldToPlayer(this.getVelocity(), gravityDirection);
                if (playerVelocity.y > -0.5D) {
                    this.fallDistance = 1.0F;
                }

                Vec3d playerRotationVector = RotationUtil.vecWorldToPlayer(this.getRotationVector(), gravityDirection);
                float playerPitch = this.getPitch() * 0.017453292F;
                double playerHorizontalRotationLength = Math.sqrt(playerRotationVector.x * playerRotationVector.x + playerRotationVector.z * playerRotationVector.z);
                double playerHorizontalVelocityLength = playerVelocity.horizontalLength();
                double playerRotationLength = playerRotationVector.length();
                float playerCosPitch = MathHelper.cos(playerPitch);
                playerCosPitch = (float)((double) playerCosPitch * (double) playerCosPitch * Math.min(1.0D, playerRotationLength / 0.4D));
                playerVelocity = RotationUtil.vecWorldToPlayer(this.getVelocity(), gravityDirection).add(0.0D, gravity * (-1.0D + (double) playerCosPitch * 0.75D), 0.0D);
                double playerHorizontalVelocityLength1;
                if (playerVelocity.y < 0.0D && playerHorizontalRotationLength > 0.0D) {
                    playerHorizontalVelocityLength1 = playerVelocity.y * -0.1D * (double) playerCosPitch;
                    playerVelocity = playerVelocity.add(playerRotationVector.x * playerHorizontalVelocityLength1 / playerHorizontalRotationLength, playerHorizontalVelocityLength1, playerRotationVector.z * playerHorizontalVelocityLength1 / playerHorizontalRotationLength);
                }

                if (playerPitch < 0.0F && playerHorizontalRotationLength > 0.0D) {
                    playerHorizontalVelocityLength1 = playerHorizontalVelocityLength * (double)(-MathHelper.sin(playerPitch)) * 0.04D;
                    playerVelocity = playerVelocity.add(-playerRotationVector.x * playerHorizontalVelocityLength1 / playerHorizontalRotationLength, playerHorizontalVelocityLength1 * 3.2D, -playerRotationVector.z * playerHorizontalVelocityLength1 / playerHorizontalRotationLength);
                }

                if (playerHorizontalRotationLength > 0.0D) {
                    playerVelocity = playerVelocity.add((playerRotationVector.x / playerHorizontalRotationLength * playerHorizontalVelocityLength - playerVelocity.x) * 0.1D, 0.0D, (playerRotationVector.z / playerHorizontalRotationLength * playerHorizontalVelocityLength - playerVelocity.z) * 0.1D);
                }

                this.setVelocity(RotationUtil.vecPlayerToWorld(playerVelocity.multiply(0.9900000095367432D, 0.9800000190734863D, 0.9900000095367432D), gravityDirection));
                this.move(MovementType.SELF, this.getVelocity());
                if (this.horizontalCollision && !this.world.isClient) {
                    playerHorizontalVelocityLength1 = RotationUtil.vecWorldToPlayer(this.getVelocity(), gravityDirection).horizontalLength();
                    double horizontalVelocityDelta = playerHorizontalVelocityLength - playerHorizontalVelocityLength1;
                    float damage = (float)(horizontalVelocityDelta * 10.0D - 3.0D);
                    if (damage > 0.0F) {
                        this.playSound(this.getFallSound((int) damage), 1.0F, 1.0F);
                        this.damage(DamageSource.FLY_INTO_WALL, damage);
                    }
                }

                if (this.onGround && !this.world.isClient) {
                    this.setFlag(Entity.FALL_FLYING_FLAG_INDEX, false);
                }
            } else {
                BlockPos velocityAffectingPos = this.getVelocityAffectingPos();
                float slipperiness = this.world.getBlockState(velocityAffectingPos).getBlock().getSlipperiness();
                movementSpeedMultiplier = this.onGround ? slipperiness * 0.91F : 0.91F;
                Vec3d playerVelocity = RotationUtil.vecWorldToPlayer(this.method_26318(movementInput, slipperiness), gravityDirection);
                double playerVelocityY = playerVelocity.y;
                if (this.hasStatusEffect(StatusEffects.LEVITATION)) {
                    playerVelocityY += (0.05D * (double)(this.getStatusEffect(StatusEffects.LEVITATION).getAmplifier() + 1) - playerVelocity.y) * 0.2D;
                    this.fallDistance = 0.0F;
                } else if (this.world.isClient && !this.world.isChunkLoaded(velocityAffectingPos)) {
                    if (this.getY() > (double)this.world.getBottomY()) {
                        playerVelocityY = -0.1D;
                    } else {
                        playerVelocityY = 0.0D;
                    }
                } else if (!this.hasNoGravity()) {
                    playerVelocityY -= gravity;
                }

                if (this.hasNoDrag()) {
                    this.setVelocity(RotationUtil.vecPlayerToWorld(playerVelocity.x, playerVelocityY, playerVelocity.z, gravityDirection));
                } else {
                    this.setVelocity(RotationUtil.vecPlayerToWorld(playerVelocity.x * (double) movementSpeedMultiplier, playerVelocityY * 0.9800000190734863D, playerVelocity.z * (double) movementSpeedMultiplier, gravityDirection));
                }
            }
        }

        this.updateLimbs((LivingEntity)(Object) this, this instanceof Flutterer);
    }

    @Redirect(
            method = "jump",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/LivingEntity;getVelocity()Lnet/minecraft/util/math/Vec3d;",
                    ordinal = 0
            )
    )
    private Vec3d redirect_jump_getVelocity_0(LivingEntity livingEntity) {
        Vec3d vec3d = livingEntity.getVelocity();

        if(livingEntity instanceof PlayerEntity) {
            PlayerEntityAccessor playerEntityAccessor = (PlayerEntityAccessor) livingEntity;
            Direction gravityDirection = playerEntityAccessor.gravitychanger$getGravityDirection();

            vec3d = RotationUtil.vecWorldToPlayer(vec3d, gravityDirection);
        }

        return vec3d;
    }

    @Redirect(
            method = "jump",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/LivingEntity;setVelocity(DDD)V",
                    ordinal = 0
            )
    )
    private void redirect_jump_setVelocity_0(LivingEntity livingEntity, double x, double y, double z) {
        if(!(livingEntity instanceof PlayerEntity)) {
            livingEntity.setVelocity(x, y, z);
            return;
        }

        PlayerEntityAccessor playerEntityAccessor = (PlayerEntityAccessor) livingEntity;
        Direction gravityDirection = playerEntityAccessor.gravitychanger$getGravityDirection();

        livingEntity.setVelocity(RotationUtil.vecPlayerToWorld(x, y, z, gravityDirection));
    }

    @Redirect(
            method = "jump",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/LivingEntity;getVelocity()Lnet/minecraft/util/math/Vec3d;",
                    ordinal = 1
            )
    )
    private Vec3d redirect_jump_getVelocity_1(LivingEntity livingEntity) {
        Vec3d vec3d = livingEntity.getVelocity();

        if(livingEntity instanceof PlayerEntity) {
            PlayerEntityAccessor playerEntityAccessor = (PlayerEntityAccessor) livingEntity;
            Direction gravityDirection = playerEntityAccessor.gravitychanger$getGravityDirection();

            vec3d = RotationUtil.vecWorldToPlayer(vec3d, gravityDirection);
        }

        return vec3d;
    }

    @Redirect(
            method = "jump",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/LivingEntity;setVelocity(Lnet/minecraft/util/math/Vec3d;)V",
                    ordinal = 0
            )
    )
    private void redirect_jump_setVelocity_0(LivingEntity livingEntity, Vec3d vec3d) {
        if(livingEntity instanceof PlayerEntity) {
            PlayerEntityAccessor playerEntityAccessor = (PlayerEntityAccessor) livingEntity;
            Direction gravityDirection = playerEntityAccessor.gravitychanger$getGravityDirection();

            vec3d = RotationUtil.vecPlayerToWorld(vec3d, gravityDirection);
        }

        livingEntity.setVelocity(vec3d);
    }

    @Redirect(
            method = "knockDownwards",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/LivingEntity;getVelocity()Lnet/minecraft/util/math/Vec3d;",
                    ordinal = 0
            )
    )
    private Vec3d redirect_knockDownwards_getVelocity_0(LivingEntity livingEntity) {
        Vec3d vec3d = livingEntity.getVelocity();

        if(livingEntity instanceof PlayerEntity) {
            PlayerEntityAccessor playerEntityAccessor = (PlayerEntityAccessor) livingEntity;
            Direction gravityDirection = playerEntityAccessor.gravitychanger$getGravityDirection();

            vec3d = RotationUtil.vecWorldToPlayer(vec3d, gravityDirection);
        }

        return vec3d;
    }

    @Redirect(
            method = "knockDownwards",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/LivingEntity;setVelocity(Lnet/minecraft/util/math/Vec3d;)V",
                    ordinal = 0
            )
    )
    private void redirect_knockDownwards_setVelocity_0(LivingEntity livingEntity, Vec3d vec3d) {
        if(livingEntity instanceof PlayerEntity) {
            PlayerEntityAccessor playerEntityAccessor = (PlayerEntityAccessor) livingEntity;
            Direction gravityDirection = playerEntityAccessor.gravitychanger$getGravityDirection();

            vec3d = RotationUtil.vecPlayerToWorld(vec3d, gravityDirection);
        }

        livingEntity.setVelocity(vec3d);
    }

    @Redirect(
            method = "swimUpward",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/LivingEntity;getVelocity()Lnet/minecraft/util/math/Vec3d;",
                    ordinal = 0
            )
    )
    private Vec3d redirect_swimUpward_getVelocity_0(LivingEntity livingEntity) {
        Vec3d vec3d = livingEntity.getVelocity();

        if(livingEntity instanceof PlayerEntity) {
            PlayerEntityAccessor playerEntityAccessor = (PlayerEntityAccessor) livingEntity;
            Direction gravityDirection = playerEntityAccessor.gravitychanger$getGravityDirection();

            vec3d = RotationUtil.vecWorldToPlayer(vec3d, gravityDirection);
        }

        return vec3d;
    }

    @Redirect(
            method = "swimUpward",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/LivingEntity;setVelocity(Lnet/minecraft/util/math/Vec3d;)V",
                    ordinal = 0
            )
    )
    private void redirect_swimUpward_setVelocity_0(LivingEntity livingEntity, Vec3d vec3d) {
        if(livingEntity instanceof PlayerEntity) {
            PlayerEntityAccessor playerEntityAccessor = (PlayerEntityAccessor) livingEntity;
            Direction gravityDirection = playerEntityAccessor.gravitychanger$getGravityDirection();

            vec3d = RotationUtil.vecPlayerToWorld(vec3d, gravityDirection);
        }

        livingEntity.setVelocity(vec3d);
    }

    @Redirect(
            method = "playBlockFallSound",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/BlockState;",
                    ordinal = 0
            )
    )
    private BlockState redirect_playBlockFallSound_getBlockState_0(World world, BlockPos blockPos) {
        if((Object) this instanceof PlayerEntity) {
            PlayerEntityAccessor playerEntityAccessor = (PlayerEntityAccessor) this;
            Direction gravityDirection = playerEntityAccessor.gravitychanger$getGravityDirection();

            blockPos = new BlockPos(this.getPos().add(RotationUtil.vecPlayerToWorld(0, -0.20000000298023224D, 0, gravityDirection)));
        }

        return world.getBlockState(blockPos);
    }

    @Redirect(
            method = "method_26318",
            at = @At(
                    value = "NEW",
                    target = "net/minecraft/util/math/Vec3d",
                    ordinal = 0
            )
    )
    private Vec3d redirect_method_26318_new_0(double x, double y, double z) {
        if(!((Object) this instanceof PlayerEntity)) {
            return new Vec3d(x, y, z);
        }

        PlayerEntityAccessor playerEntityAccessor = (PlayerEntityAccessor) this;
        Direction gravityDirection = playerEntityAccessor.gravitychanger$getGravityDirection();

        Vec3d maskXZ = RotationUtil.maskPlayerToWorld(1.0D, 0.0D, 1.0D, gravityDirection);
        return this.getVelocity().multiply(maskXZ).add(RotationUtil.vecPlayerToWorld(0.0D, 0.2D, 0.0D, gravityDirection));
    }

    @ModifyVariable(
            method = "applyClimbingSpeed",
            at = @At("HEAD"),
            ordinal = 0
    )
    private Vec3d modify_applyClimbingSpeed_motion_0(Vec3d motion) {
        if((Object) this instanceof PlayerEntity) {
            PlayerEntityAccessor playerEntityAccessor = (PlayerEntityAccessor) this;
            Direction gravityDirection = playerEntityAccessor.gravitychanger$getGravityDirection();

            motion = RotationUtil.vecWorldToPlayer(motion, gravityDirection);
        }

        return motion;
    }

    @Inject(
            method = "applyClimbingSpeed",
            at = @At("RETURN"),
            cancellable = true
    )
    private void inject_applyClimbingSpeed(Vec3d motion, CallbackInfoReturnable<Vec3d> cir) {
        if(!((Object) this instanceof PlayerEntity)) return;
        PlayerEntityAccessor playerEntityAccessor = (PlayerEntityAccessor) this;
        Direction gravityDirection = playerEntityAccessor.gravitychanger$getGravityDirection();

        cir.setReturnValue(RotationUtil.vecPlayerToWorld(cir.getReturnValue(), gravityDirection));
    }

    @Redirect(
            method = "canSee",
            at = @At(
                    value = "NEW",
                    target = "net/minecraft/util/math/Vec3d",
                    ordinal = 0
            )
    )
    private Vec3d redirect_canSee_new_0(double x, double y, double z) {
        return this.getEyePos();
    }

    @Redirect(
            method = "canSee",
            at = @At(
                    value = "NEW",
                    target = "net/minecraft/util/math/Vec3d",
                    ordinal = 1
            )
    )
    private Vec3d redirect_canSee_new_1(double x, double y, double z, Entity entity) {
        return entity.getEyePos();
    }

    @Inject(
            method = "getBoundingBox",
            at = @At("HEAD"),
            cancellable = true
    )
    private void inject_getBoundingBox(EntityPose pose, CallbackInfoReturnable<Box> cir) {
        if(!((Object) this instanceof PlayerEntity)) return;
        PlayerEntityAccessor playerEntityAccessor = (PlayerEntityAccessor) this;
        Direction gravityDirection = playerEntityAccessor.gravitychanger$getGravityDirection();

        Box box = this.getDimensions(pose).getBoxAt(0, 0, 0);
        cir.setReturnValue(RotationUtil.boxPlayerToWorld(box, gravityDirection));
    }

    @Inject(
            method = "updateLimbs",
            at = @At("HEAD"),
            cancellable = true
    )
    private void inject_updateLimbs(LivingEntity entity, boolean flutter, CallbackInfo ci) {
        if(!(entity instanceof PlayerEntity)) return;
        PlayerEntityAccessor playerEntityAccessor = (PlayerEntityAccessor) entity;
        Direction gravityDirection = playerEntityAccessor.gravitychanger$getGravityDirection();

        ci.cancel();

        Vec3d playerPosDelta = RotationUtil.vecWorldToPlayer(entity.getX() - entity.prevX, entity.getY() - entity.prevY, entity.getZ() - entity.prevZ, gravityDirection);

        entity.lastLimbDistance = entity.limbDistance;
        double d = playerPosDelta.x;
        double e = flutter ? playerPosDelta.y : 0.0D;
        double f = playerPosDelta.z;
        float g = (float)Math.sqrt(d * d + e * e + f * f) * 4.0F;
        if (g > 1.0F) {
            g = 1.0F;
        }

        entity.limbDistance += (g - entity.limbDistance) * 0.4F;
        entity.limbAngle += entity.limbDistance;
    }

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/LivingEntity;getX()D",
                    ordinal = 0
            )
    )
    private double redirect_tick_getX_0(LivingEntity livingEntity) {
        if(!(livingEntity instanceof PlayerEntity)) return livingEntity.getX();
        PlayerEntityAccessor playerEntityAccessor = (PlayerEntityAccessor) livingEntity;
        Direction gravityDirection = playerEntityAccessor.gravitychanger$getGravityDirection();

        return RotationUtil.vecWorldToPlayer(livingEntity.getX() - livingEntity.prevX, livingEntity.getY() - livingEntity.prevY, livingEntity.getZ() - livingEntity.prevZ, gravityDirection).x + livingEntity.prevX;
    }

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/LivingEntity;getZ()D",
                    ordinal = 0
            )
    )
    private double redirect_tick_getZ_0(LivingEntity livingEntity) {
        if(!(livingEntity instanceof PlayerEntity)) return livingEntity.getX();
        PlayerEntityAccessor playerEntityAccessor = (PlayerEntityAccessor) livingEntity;
        Direction gravityDirection = playerEntityAccessor.gravitychanger$getGravityDirection();

        return RotationUtil.vecWorldToPlayer(livingEntity.getX() - livingEntity.prevX, livingEntity.getY() - livingEntity.prevY, livingEntity.getZ() - livingEntity.prevZ, gravityDirection).z + livingEntity.prevZ;
    }

    @Redirect(
            method = "takeKnockback",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/LivingEntity;setVelocity(DDD)V",
                    ordinal = 0
            )
    )
    private void redirect_takeKnockback_setVelocity_0(LivingEntity target, double x, double y, double z) {
        if(!(target instanceof PlayerEntity)) {
            target.setVelocity(x, y, z);
            return;
        }
        PlayerEntityAccessor playerEntityAccessor = (PlayerEntityAccessor) target;
        Direction gravityDirection = playerEntityAccessor.gravitychanger$getGravityDirection();

        target.setVelocity(RotationUtil.vecPlayerToWorld(x, y, z, gravityDirection));
    }

    @Redirect(
            method = "damage",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/Entity;getX()D",
                    ordinal = 0
            )
    )
    private double redirect_damage_getX_0(Entity attacker) {
        Vec3d attackerPos = attacker instanceof PlayerEntity ? attacker.getEyePos() : attacker.getPos();
        if(!((Object) this instanceof PlayerEntity)) {
            return attackerPos.x;
        }
        PlayerEntityAccessor playerEntityAccessor = (PlayerEntityAccessor) this;
        Direction gravityDirection = playerEntityAccessor.gravitychanger$getGravityDirection();

        return RotationUtil.vecWorldToPlayer(attackerPos, gravityDirection).x;
    }

    @Redirect(
            method = "damage",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/Entity;getZ()D",
                    ordinal = 0
            )
    )
    private double redirect_damage_getZ_0(Entity attacker) {
        Vec3d attackerPos = attacker instanceof PlayerEntity ? attacker.getEyePos() : attacker.getPos();
        if(!((Object) this instanceof PlayerEntity)) {
            return attackerPos.z;
        }
        PlayerEntityAccessor playerEntityAccessor = (PlayerEntityAccessor) this;
        Direction gravityDirection = playerEntityAccessor.gravitychanger$getGravityDirection();

        return RotationUtil.vecWorldToPlayer(attackerPos, gravityDirection).z;
    }

    @Redirect(
            method = "damage",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/LivingEntity;getX()D",
                    ordinal = 0
            )
    )
    private double redirect_damage_getX_0(LivingEntity target) {
        if(!(target instanceof PlayerEntity)) {
            return target.getX();
        }
        PlayerEntityAccessor playerEntityAccessor = (PlayerEntityAccessor) target;
        Direction gravityDirection = playerEntityAccessor.gravitychanger$getGravityDirection();

        return RotationUtil.vecWorldToPlayer(target.getPos(), gravityDirection).x;
    }

    @Redirect(
            method = "damage",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/LivingEntity;getZ()D",
                    ordinal = 0
            )
    )
    private double redirect_damage_getZ_0(LivingEntity target) {
        if(!(target instanceof PlayerEntity)) {
            return target.getZ();
        }
        PlayerEntityAccessor playerEntityAccessor = (PlayerEntityAccessor) target;
        Direction gravityDirection = playerEntityAccessor.gravitychanger$getGravityDirection();

        return RotationUtil.vecWorldToPlayer(target.getPos(), gravityDirection).z;
    }

    @Redirect(
            method = "knockback",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/LivingEntity;getX()D",
                    ordinal = 0
            )
    )
    private double redirect_knockback_getX_0(LivingEntity target) {
        if(!(target instanceof PlayerEntity)) {
            return target.getX();
        }
        PlayerEntityAccessor playerEntityAccessor = (PlayerEntityAccessor) target;
        Direction gravityDirection = playerEntityAccessor.gravitychanger$getGravityDirection();

        return RotationUtil.vecWorldToPlayer(target.getPos(), gravityDirection).x;
    }

    @Redirect(
            method = "knockback",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/LivingEntity;getZ()D",
                    ordinal = 0
            )
    )
    private double redirect_knockback_getZ_0(LivingEntity target) {
        if(!(target instanceof PlayerEntity)) {
            return target.getZ();
        }
        PlayerEntityAccessor playerEntityAccessor = (PlayerEntityAccessor) target;
        Direction gravityDirection = playerEntityAccessor.gravitychanger$getGravityDirection();

        return RotationUtil.vecWorldToPlayer(target.getPos(), gravityDirection).z;
    }

    @Redirect(
            method = "knockback",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/LivingEntity;getX()D",
                    ordinal = 1
            )
    )
    private double redirect_knockback_getX_1(LivingEntity attacker, LivingEntity target) {
        Vec3d pos = attacker instanceof PlayerEntity ? attacker.getEyePos() : attacker.getPos();
        if(!(target instanceof PlayerEntity)) {
            return pos.x;
        }
        PlayerEntityAccessor playerEntityAccessor = (PlayerEntityAccessor) target;
        Direction gravityDirection = playerEntityAccessor.gravitychanger$getGravityDirection();

        return RotationUtil.vecWorldToPlayer(pos, gravityDirection).x;
    }

    @Redirect(
            method = "knockback",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/LivingEntity;getZ()D",
                    ordinal = 1
            )
    )
    private double redirect_knockback_getZ_1(LivingEntity attacker, LivingEntity target) {
        Vec3d pos = attacker instanceof PlayerEntity ? attacker.getEyePos() : attacker.getPos();
        if(!(target instanceof PlayerEntity)) {
            return pos.z;
        }
        PlayerEntityAccessor playerEntityAccessor = (PlayerEntityAccessor) target;
        Direction gravityDirection = playerEntityAccessor.gravitychanger$getGravityDirection();

        return RotationUtil.vecWorldToPlayer(pos, gravityDirection).z;
    }

    @Redirect(
            method = "baseTick",
            at = @At(
                    value = "NEW",
                    target = "net/minecraft/util/math/BlockPos",
                    ordinal = 0
            )
    )
    private BlockPos redirect_baseTick_new_0(double x, double y, double z) {
        if(!((Object) this instanceof PlayerEntity)) {
            return new BlockPos(x, y, z);
        }

        return new BlockPos(this.getEyePos());
    }

    @Redirect(
            method = "spawnItemParticles",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/math/Vec3d;add(DDD)Lnet/minecraft/util/math/Vec3d;",
                    ordinal = 0
            )
    )
    private Vec3d redirect_spawnItemParticles_add_0(Vec3d vec3d, double x, double y, double z) {
        if(!((Object) this instanceof PlayerEntity)) {
            return vec3d.add(x, y, z);
        }
        PlayerEntityAccessor playerEntityAccessor = (PlayerEntityAccessor) this;
        Direction gravityDirection = playerEntityAccessor.gravitychanger$getGravityDirection();

        return this.getEyePos().add(RotationUtil.vecPlayerToWorld(vec3d, gravityDirection));
    }

    @ModifyVariable(
            method = "spawnItemParticles",
            at = @At(
                    value = "INVOKE_ASSIGN",
                    target = "Lnet/minecraft/util/math/Vec3d;rotateY(F)Lnet/minecraft/util/math/Vec3d;",
                    ordinal = 0
            ),
            ordinal = 0
    )
    private Vec3d modify_spawnItemParticles_Vec3d_0(Vec3d vec3d) {
        if(!((Object) this instanceof PlayerEntity)) {
            return vec3d;
        }
        PlayerEntityAccessor playerEntityAccessor = (PlayerEntityAccessor) this;
        Direction gravityDirection = playerEntityAccessor.gravitychanger$getGravityDirection();

        return RotationUtil.vecPlayerToWorld(vec3d, gravityDirection);
    }

    @ModifyArgs(
            method = "tickStatusEffects",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)V",
                    ordinal = 0
            )
    )
    private void modify_tickStatusEffects_addParticle_0(Args args) {
        if(!((Object) this instanceof PlayerEntity)) return;
        PlayerEntityAccessor playerEntityAccessor = (PlayerEntityAccessor) this;
        Direction gravityDirection = playerEntityAccessor.gravitychanger$getGravityDirection();

        Vec3d vec3d = this.getPos().subtract(RotationUtil.vecPlayerToWorld(this.getPos().subtract(args.get(1), args.get(2), args.get(3)), gravityDirection));
        args.set(1, vec3d.x);
        args.set(2, vec3d.y);
        args.set(3, vec3d.z);
    }

    @ModifyArgs(
            method = "addDeathParticles",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)V",
                    ordinal = 0
            )
    )
    private void modify_addDeathParticless_addParticle_0(Args args) {
        if(!((Object) this instanceof PlayerEntity)) return;
        PlayerEntityAccessor playerEntityAccessor = (PlayerEntityAccessor) this;
        Direction gravityDirection = playerEntityAccessor.gravitychanger$getGravityDirection();

        Vec3d vec3d = this.getPos().subtract(RotationUtil.vecPlayerToWorld(this.getPos().subtract(args.get(1), args.get(2), args.get(3)), gravityDirection));
        args.set(1, vec3d.x);
        args.set(2, vec3d.y);
        args.set(3, vec3d.z);
    }
}