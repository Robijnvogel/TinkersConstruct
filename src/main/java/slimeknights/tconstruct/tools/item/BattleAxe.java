package slimeknights.tconstruct.tools.item;

import com.google.common.collect.ImmutableList;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;

import java.util.List;

import slimeknights.tconstruct.library.materials.HeadMaterialStats;
import slimeknights.tconstruct.library.materials.Material;
import slimeknights.tconstruct.library.tinkering.Category;
import slimeknights.tconstruct.library.tinkering.PartMaterialType;
import slimeknights.tconstruct.library.tools.AoeToolCore;
import slimeknights.tconstruct.library.tools.ToolNBT;
import slimeknights.tconstruct.library.utils.ToolHelper;
import slimeknights.tconstruct.tools.TinkerTools;

// Ability: Berserk. Can be activated on demand, gives a speedboost, jump boost, mining boost, damage boost. Also makes you take more damage
// Screen turns red/with a red border (steal from thaumcraft) and you can't switch item while berserk is active
public class BattleAxe extends AoeToolCore {

  public BattleAxe() {
    super(PartMaterialType.handle(TinkerTools.toughToolRod),
          PartMaterialType.head(TinkerTools.broadAxeHead),
          PartMaterialType.head(TinkerTools.broadAxeHead),
          PartMaterialType.extra(TinkerTools.toughBinding));

    addCategory(Category.WEAPON);

    setHarvestLevel("axe", 0);
  }

  @Override
  public boolean canUseSecondaryItem() {
    return false;
  }

  @Override
  public ImmutableList<BlockPos> getAOEBlocks(ItemStack stack, World world, EntityPlayer player, BlockPos origin) {
    if(!ToolHelper.isToolEffective2(stack, world.getBlockState(origin))) {
      return ImmutableList.of();
    }
    return ToolHelper.calcAOEBlocks(stack, world, player, origin, 2, 2, 1);
  }

  @Override
  public float damagePotential() {
    return 2.0f;
  }

  @Override
  public float damageCutoff() {
    return 30f;
  }

  @Override
  public NBTTagCompound buildTag(List<Material> materials) {
    HeadMaterialStats handle = materials.get(0).getStats(HeadMaterialStats.TYPE);
    HeadMaterialStats head1 = materials.get(1).getStats(HeadMaterialStats.TYPE);
    HeadMaterialStats head2 = materials.get(2).getStats(HeadMaterialStats.TYPE);
    HeadMaterialStats binding = materials.get(3).getStats(HeadMaterialStats.TYPE);

    ToolNBT data = new ToolNBT();

    data.harvestLevel = Math.max(head1.harvestLevel, head2.harvestLevel);

    data.durability = (head1.durability + head2.durability)/2;
    //data.handle(handle).extra(binding);

    //data.durability *= 1f + 0.15f * (binding.extraQuality - 0.5f);
    //data.speed *= 1f + 0.1f * (handle.modifier * handle.miningspeed);
    data.speed *= 0.5f; // slower because AOE
    // no base damage but higher damage potential
    data.attack = (head1.attack + head2.attack) * 3f / 2f;
    //data.attack *= 1f + 0.1f * handle.modifier * binding.extraQuality;

    /*
    data.durability += head1.durability * (0.2f * head2.extraQuality + 0.2f * binding.extraQuality + 0.1f * handle.modifier);
    data.durability += head2.durability * (0.2f * head1.extraQuality + 0.2f * binding.extraQuality + 0.1f * handle.modifier);
    data.durability += binding.durability * binding.extraQuality * 0.5f;
    data.durability += handle.durability * 0.1f;

    data.attack = (head1.attack + head2.attack)*2f/3f;
    data.attack += (0.2f + 0.7f * handle.modifier * binding.extraQuality) * (head1.attack + head2.attack) / 3f;

    data.speed = head1.miningspeed/2f + head2.miningspeed/2f;
    data.speed *= 0.3f + 0.3f * handle.modifier * binding.extraQuality;
*/
    data.modifiers = 2;

    return data.get();
  }
}
