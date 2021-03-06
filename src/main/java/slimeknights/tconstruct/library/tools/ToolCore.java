package slimeknights.tconstruct.library.tools;

import com.google.common.collect.Sets;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.BlockPos;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import slimeknights.tconstruct.common.ClientProxy;
import slimeknights.tconstruct.library.TinkerRegistry;
import slimeknights.tconstruct.library.Util;
import slimeknights.tconstruct.library.materials.ExtraMaterialStats;
import slimeknights.tconstruct.library.materials.HandleMaterialStats;
import slimeknights.tconstruct.library.materials.HeadMaterialStats;
import slimeknights.tconstruct.library.materials.Material;
import slimeknights.tconstruct.library.tinkering.Category;
import slimeknights.tconstruct.library.tinkering.PartMaterialType;
import slimeknights.tconstruct.library.tinkering.TinkersItem;
import slimeknights.tconstruct.library.traits.ITrait;
import slimeknights.tconstruct.library.utils.TagUtil;
import slimeknights.tconstruct.library.utils.TinkerUtil;
import slimeknights.tconstruct.library.utils.ToolHelper;
import slimeknights.tconstruct.library.utils.TooltipBuilder;
import slimeknights.tconstruct.tools.TinkerTools;
import slimeknights.tconstruct.tools.traits.ToolGrowth;

/**
 * Intermediate abstraction layer for all tools/melee weapons. This class has all the callbacks for blocks and enemies
 * so tools and weapons can share behaviour.
 */
public abstract class ToolCore extends TinkersItem {

  protected final static int DEFAULT_MODIFIERS = 3;
  protected final static ToolGrowth toolGrowth;

  static {
    toolGrowth = new ToolGrowth();
    TinkerRegistry.addTrait(toolGrowth);
  }

  public ToolCore(PartMaterialType... requiredComponents) {
    super(requiredComponents);

    this.setCreativeTab(TinkerRegistry.tabTools);
    this.setNoRepair(); // >_>

    TinkerRegistry.registerTool(this);
    addCategory(Category.TOOL);
  }

  @Override
  public int getMaxDamage(ItemStack stack) {
    return ToolHelper.getDurabilityStat(stack);
  }

  @Override
  public boolean isDamageable() {
    return true;
  }

  /* Tool and Weapon specific properties */

  /** Multiplier applied to the actual mining speed of the tool
   *  Internally a hammer and pick have the same speed, but a hammer is 2/3 slower
   */
  public float miningSpeedModifier() {
    return 1f;
  }

  /** Multiplier for damage from materials. Should be fixed per tool. */
  public abstract float damagePotential();

  /**
   * A fixed damage value where the calculations start to apply dimishing returns.
   * Basically if you'd hit more than that damage with this tool, the damage is gradually reduced depending on how much the cutoff is exceeded.
   */
  public float damageCutoff() {
    return 15.0f; // in general this should be sufficient and only needs increasing if it's a stronger weapon
    // fun fact: diamond sword with sharpness V has 15 damage
  }

  /**
   * Allows you to speed up the attack. 0 is standard attack. 5 is max speed. Negative values are not possible.
   */
  public int attackSpeed() {
    return 0;
  }

  /**
   * Knockback modifier. Basically this takes the vanilla knockback on hit and modifies it by this factor.
   */
  public float knockback() {
    return 1.0f;
  }

  /**
   * Actually deal damage to the entity we hit. Can be overridden for special behaviour
   *
   * @return True if the entity was hit. Usually the return value of {@link Entity#attackEntityFrom(DamageSource, float)}
   */
  public boolean dealDamage(ItemStack stack, EntityPlayer player, EntityLivingBase entity, float damage) {
    return entity.attackEntityFrom(DamageSource.causePlayerDamage(player), damage);
  }

  /**
   * Called when an entity is getting damaged with the tool.
   * Reduce the tools durability accordingly
   */
  public void reduceDurabilityOnHit(ItemStack stack, EntityPlayer player, int damage) {
    damage = Math.min(1, damage/3);
    if(!hasCategory(Category.WEAPON)) {
      damage *= 2;
    }
    ToolHelper.damageTool(stack, damage, player);
  }

  @Override
  public float getDigSpeed(ItemStack itemstack, IBlockState state) {
    if(isEffective(state.getBlock()) || ToolHelper.isToolEffective(itemstack, state)) {
      return ToolHelper.calcDigSpeed(itemstack, state);
    }
    return super.getDigSpeed(itemstack, state);
  }

  public boolean isEffective(Block block) {
    return false;
  }

  @Override
  public boolean canHarvestBlock(Block block, ItemStack itemStack) {
    return isEffective(block);
  }

  @Override
  public boolean onBlockStartBreak(ItemStack itemstack, BlockPos pos, EntityPlayer player) {
    if(this instanceof IAoeTool && ((IAoeTool) this).isAoeHarvestTool() && ToolHelper.isToolEffective2(itemstack, player.worldObj.getBlockState(pos))) {
      for(BlockPos extraPos : ((IAoeTool) this).getAOEBlocks(itemstack, player.worldObj, player, pos)) {
        ToolHelper.breakExtraBlock(itemstack, player.worldObj, player, extraPos, pos);
      }
    }

    return super.onBlockStartBreak(itemstack, pos, player);
  }

  @Override
  public boolean onLeftClickEntity(ItemStack stack, EntityPlayer player, Entity entity) {
    return ToolHelper.attackEntity(stack, this, player, entity);
  }

  @Override
  public boolean onEntitySwing(EntityLivingBase entityLiving, ItemStack stack) {
    if(attackSpeed() > 0) {
      int speed = Math.min(5, attackSpeed());
      ToolHelper.swingItem(speed, entityLiving);
      return true;
    }
    return super.onEntitySwing(entityLiving, stack);
  }

  public boolean canUseSecondaryItem() {
    return true;
  }

  @Override
  public boolean onItemUse(ItemStack stack, EntityPlayer playerIn, World worldIn, BlockPos pos, EnumFacing side, float hitX, float hitY, float hitZ) {
    if(canUseSecondaryItem()) {
      return ToolHelper.useSecondaryItem(stack, playerIn, worldIn, pos, side, hitX, hitY, hitZ);
    }
    return super.onItemUse(stack, playerIn, worldIn, pos, side, hitX, hitY, hitZ);
  }

  @Override
  public boolean hitEntity(ItemStack stack, EntityLivingBase target, EntityLivingBase attacker) {
    if(attackSpeed() > 0) {
      target.hurtResistantTime -= attackSpeed();
      target.hurtTime -= attackSpeed();
    }
    return super.hitEntity(stack, target, attacker);
  }

  @Override
  public String[] getInformation(ItemStack stack) {
    return getInformation(stack, false);
  }

  @Override
  public void getTooltipDetailed(ItemStack stack, List<String> tooltips) {
    tooltips.addAll(Arrays.asList(getInformation(stack, true)));
  }

  public String[] getInformation(ItemStack stack, boolean detailed) {
    TooltipBuilder info = new TooltipBuilder(stack);

    info.addDurability(!detailed);
    if(hasCategory(Category.HARVEST)) {
      info.addHarvestLevel();
      info.addMiningSpeed();
    }
    info.addAttack();

    if(ToolHelper.getFreeModifiers(stack) > 0) {
      info.addFreeModifiers();
    }

    return info.getTooltip();
  }

  @Override
  public void getTooltipComponents(ItemStack stack, List<String> tooltips) {
    tooltips.add("Not implemented :(");
  }

  @SideOnly(Side.CLIENT)
  @Override
  public FontRenderer getFontRenderer(ItemStack stack) {
    return ClientProxy.fontRenderer;
  }

  @SideOnly(Side.CLIENT)
  @Override
  public boolean hasEffect(ItemStack stack) {
    return false; // no effect for you.
  }

  @Override
  public String getItemStackDisplayName(ItemStack stack) {
    // if the tool is not named we use the repair tools for a prefix like thing
    List<Material> materials = TinkerUtil.getMaterialsFromTagList(TagUtil.getBaseMaterialsTagList(stack));
    // we save all the ones for the name in a set so we don't have the same material in it twice
    Set<Material> nameMaterials = Sets.newLinkedHashSet();

    for(int index : getRepairParts()) {
      if(index < materials.size()) {
        nameMaterials.add(materials.get(index));
      }
    }

    String itemName = super.getItemStackDisplayName(stack);

    // no material
    if(nameMaterials.isEmpty()) {
      return itemName;
    }
    // only one material - prefix
    if(nameMaterials.size() == 1) {
      return nameMaterials.iterator().next().getLocalizedItemName(itemName);
    }

    // multiple materials. we'll have to combine
    StringBuilder sb = new StringBuilder();
    Iterator<Material> iter = nameMaterials.iterator();
    Material material = iter.next();
    sb.append(material.getLocalizedName());
    while(iter.hasNext()) {
      material = iter.next();
      sb.append("-");
      sb.append(material.getLocalizedName());
    }
    sb.append(" ");
    sb.append(itemName);

    return sb.toString();
  }

  // Creative tab items
  @Override
  public void getSubItems(Item itemIn, CreativeTabs tab, List<ItemStack> subItems) {
    for(Material head : TinkerRegistry.getAllMaterials()) {
      if(!head.hasStats(HeadMaterialStats.TYPE)) {
        continue;
      }

      List<Material> mats = new ArrayList<Material>(requiredComponents.length);

      for(int i = 0; i < requiredComponents.length; i++) {
        // todo: check for applicability with stats
        mats.add(head);
      }

      ItemStack tool = buildItem(mats);
      // only valid ones
      if(hasValidMaterials(tool)) {
        subItems.add(tool);
      }
    }
  }

  @Override
  public int getHarvestLevel(ItemStack stack, String toolClass) {
    if(this.getToolClasses(stack).contains(toolClass)) {
      NBTTagCompound tag = TagUtil.getToolTag(stack);
      // will return 0 if the tag has no info anyway
      return ToolHelper.getHarvestLevelStat(stack);
    }
    return super.getHarvestLevel(stack, toolClass);
  }

  /** A simple string identifier for the tool, used for identification in texture generation etc. */
  public String getIdentifier() {
    return Util.getItemLocation(this).getResourcePath();
  }

  /** The tools name completely without material information */
  public String getLocalizedToolName() {
    return Util.translate(getUnlocalizedName() + ".name");
  }

  /** The tools name with the given material. e.g. "Wooden Pickaxe" */
  public String getLocalizedToolName(Material material) {
    return material.getLocalizedItemName(getLocalizedToolName());
  }

  /** Returns info about the Tool. Displayed in the tool stations etc. */
  public String getLocalizedDescription() {
    return Util.translate(getUnlocalizedName() + ".desc");
  }

  /* Additional Trait callbacks */

  @Override
  public void onUpdate(ItemStack stack, World worldIn, Entity entityIn, int itemSlot, boolean isSelected) {
    super.onUpdate(stack, worldIn, entityIn, itemSlot, isSelected);

    NBTTagList list = TagUtil.getTraitsTagList(stack);
    for(int i = 0; i < list.tagCount(); i++) {
      ITrait trait = TinkerRegistry.getTrait(list.getStringTagAt(i));
      if(trait != null) {
        trait.onUpdate(stack, worldIn, entityIn, itemSlot, isSelected);
      }
    }
  }

  @Override
  public boolean onBlockDestroyed(ItemStack stack, World worldIn, Block blockIn, BlockPos pos, EntityLivingBase playerIn) {
    if(ToolHelper.isBroken(stack)) {
      return false;
    }

    boolean effective = isEffective(blockIn) || ToolHelper.isToolEffective(stack, worldIn.getBlockState(pos));
    int damage = effective ? 1 : 2;

    afterBlockBreak(stack, worldIn, blockIn, pos, playerIn, damage, effective);

    return hasCategory(Category.TOOL);
  }

  public void afterBlockBreak(ItemStack stack, World world, Block block, BlockPos pos, EntityLivingBase player, int damage, boolean wasEffective) {
    NBTTagList list = TagUtil.getTraitsTagList(stack);
    for(int i = 0; i < list.tagCount(); i++) {
      ITrait trait = TinkerRegistry.getTrait(list.getStringTagAt(i));
      if(trait != null) {
        trait.afterBlockBreak(stack, world, block, pos, player, wasEffective);
      }
    }

    ToolHelper.damageTool(stack, damage, player);
  }

  // elevate to public
  public MovingObjectPosition getMovingObjectPositionFromPlayer(World worldIn, EntityPlayer playerIn, boolean useLiquids) {
    return super.getMovingObjectPositionFromPlayer(worldIn, playerIn, useLiquids);
  }

  protected void preventSlowDown(Entity entityIn, float originalSpeed) {
    TinkerTools.proxy.preventPlayerSlowdown(entityIn, originalSpeed, this);
  }

  @Override
  public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
    // we simply return false. This should be cover any cases and probably 1-2 edge cases where it shouldn't..
    // ..but I doubt anybody will notice ;o
    return false;
  }

  /**
   * Builds a default tool from:
   * 1. Handle
   * 2. Head
   * 3. Accessoire (if present)
   */
  protected ToolNBT buildDefaultTag(List<Material> materials) {
    ToolNBT data = new ToolNBT();

    if(materials.size() >= 2) {
      HandleMaterialStats handle = materials.get(0).getStatsOrUnknown(HandleMaterialStats.TYPE);
      HeadMaterialStats head = materials.get(1).getStatsOrUnknown(HeadMaterialStats.TYPE);
      // start with head
      data.head(head);

      // add in accessoires if present
      if(materials.size() >= 3) {
        ExtraMaterialStats binding = materials.get(2).getStatsOrUnknown(ExtraMaterialStats.TYPE);
        data.extra(binding);
      }

      // calculate handle impact
      data.handle(handle);
    }

    // 3 free modifiers
    data.modifiers = DEFAULT_MODIFIERS;

    return data;
  }
}
