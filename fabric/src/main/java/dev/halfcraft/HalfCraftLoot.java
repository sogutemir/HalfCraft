package dev.halfcraft;

import java.util.random.RandomGenerator;

/** Fixed campaign balance; pure rolls also run without Minecraft for regression checks. */
public final class HalfCraftLoot {
    public record Combat(int arrows,int feathers,int gunpowder,int string,boolean bow,boolean enchanted,int xp,int beef,boolean cooked) {}
    public static Combat combat(RandomGenerator random,int tier,boolean cooked) {
        if(tier<0 || tier>2) throw new IllegalArgumentException("Invalid enemy tier");
        boolean bow=random.nextInt(100)<35;
        return new Combat(random.nextInt(12,25),random.nextInt(4,9),random.nextInt(3,7),random.nextInt(2,5),bow,bow && random.nextInt(100)<25,tier==2?500:tier==1?200:100,random.nextInt(2,5),cooked);
    }
    public static int source(int roll,boolean diamondPickaxe) {
        if(roll<0 || roll>=1000) throw new IllegalArgumentException("Source roll outside [0,1000)");
        if(roll<(diamondPickaxe?15:5)) return 7;   // Ancient debris: 1.5% with diamond pickaxe, otherwise 0.5%.
        if(roll<(diamondPickaxe?115:25)) return 6; // Diamond ore: 10% with diamond pickaxe, otherwise 2%.
        if(roll<375) return 0; // Extra rare-source weight replaces stone: 26% with diamond pickaxe, otherwise 35%.
        if(roll<575) return 1; // Oak log: 20%.
        if(roll<675) return 2; // Dirt: 10%.
        if(roll<825) return 3; // Coal ore: 15%.
        if(roll<975) return 4; // Iron ore: 15%.
        return 5;             // Gold ore: 2.5%; supports vanilla netherite crafting.
    }
    public static void main(String[] args) {
        int[] counts=new int[8],diamondCounts=new int[8];
        for(int i=0;i<1000;i++) { counts[source(i,false)]++; diamondCounts[source(i,true)]++; }
        if(!java.util.Arrays.equals(counts,new int[]{350,200,100,150,150,25,20,5})) throw new AssertionError(java.util.Arrays.toString(counts));
        if(!java.util.Arrays.equals(diamondCounts,new int[]{260,200,100,150,150,25,100,15})) throw new AssertionError(java.util.Arrays.toString(diamondCounts));
        var random=new java.util.Random(42); int bows=0,enchanted=0;
        for(int i=0;i<10000;i++) {
            int tier=i%3; boolean cooked=(i&1)==0; var drop=combat(random,tier,cooked);
            if(drop.arrows()<12 || drop.arrows()>24 || drop.feathers()<4 || drop.feathers()>8
                || drop.gunpowder()<3 || drop.gunpowder()>6 || drop.string()<2 || drop.string()>4
                || drop.xp()!=(tier==2?500:tier==1?200:100) || (drop.enchanted() && !drop.bow())
                || drop.beef()<2 || drop.beef()>4 || drop.cooked()!=cooked) throw new AssertionError(drop);
            if(drop.bow()) bows++; if(drop.enchanted()) enchanted++;
        }
        if(bows<3200 || bows>3800 || enchanted<700 || enchanted>1050) throw new AssertionError("Loot distribution");
        try { combat(random,3,false); throw new AssertionError("Missing tier validation"); } catch(IllegalArgumentException expected) {}
        for(boolean diamondPickaxe:new boolean[]{false,true}) {
            try { source(-1,diamondPickaxe); throw new AssertionError("Missing validation"); } catch(IllegalArgumentException expected) {}
            try { source(1000,diamondPickaxe); throw new AssertionError("Missing validation"); } catch(IllegalArgumentException expected) {}
        }
        System.out.println("Campaign loot PASS: source rates, combat quantities, bows 35%, enchanted bows 25%, XP 100/200/500, beef 2-4, fire cooks beef");
    }
}
