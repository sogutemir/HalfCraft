"""Generate health/drop authority seams; pinned SDK stays untouched, saves keep SDK classes."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
source=(root/'goldsrc/sdk/dlls/player.cpp').read_text()
for needle,body in (
    ('int CBasePlayer :: TakeDamage( entvars_t *pevInflictor, entvars_t *pevAttacker, float flDamage, int bitsDamageType )\n{',
     'if (hc_vitals::owns(this)) return hc_vitals::damage(this,pevInflictor,pevAttacker,flDamage,bitsDamageType);'),
    ('int CBasePlayer :: TakeHealth( float flHealth, int bitsDamageType )\n{',
     'if (hc_vitals::owns(this)) return hc_vitals::heal(this,flHealth);'),
):
    if source.count(needle)!=1: raise RuntimeError('Pinned SDK player seam changed: '+needle)
    source=source.replace(needle,needle+'\n\t'+body)
target=root/'goldsrc/build/player_generated.cpp'
if not target.parent.is_dir(): raise RuntimeError('Host build directory missing')
target.write_text('#include "../vitals.h"\n'+source)
seams={
    'items': [('class CItemBattery : public CItem', 'BOOL MyTouch( CBasePlayer *pPlayer )\n\t{',
        'if (hc_vitals::owns(pPlayer)) { if (!hc_vitals::repair(pPlayer,gSkillData.batteryCapacity)) return FALSE; EMIT_SOUND(pPlayer->edict(),CHAN_ITEM,"items/gunpickup2.wav",1,ATTN_NORM); return TRUE; }')],
    'h_battery': [('', '// charge the player\n\tif (m_hActivator->pev->armorvalue < 100)',
        'if (hc_vitals::owns((CBasePlayer*)(CBaseEntity*)m_hActivator)) { if (hc_vitals::repair((CBasePlayer*)(CBaseEntity*)m_hActivator,1)) m_iJuice--; m_flNextCharge=gpGlobals->time+0.1; return; }')],
    'monsters': [('', 'CBaseEntity* CBaseMonster :: DropItem ( char *pszItemName, const Vector &vecPos, const Vector &vecAng )\n{',
        'auto* player=INDEXENT(1); if (player && !player->free && Classify()==CLASS_HUMAN_MILITARY && hc_vitals::owns((CBasePlayer*)CBaseEntity::Instance(player))) return NULL;')],
}
for name,entries in seams.items():
    source=(root/f'goldsrc/sdk/dlls/{name}.cpp').read_text()
    if name in ('h_battery','monsters'): source=source.replace('#include "cbase.h"','#include "cbase.h"\n#include "player.h"')
    for anchor,needle,body in entries:
        start=source.index(anchor) if anchor else 0
        prefix,suffix=source[:start],source[start:]
        if (not anchor and suffix.count(needle)!=1) or needle not in suffix: raise RuntimeError('Pinned SDK seam changed: '+name)
        if name=='h_battery': suffix=suffix.replace(needle,'// charge the player\n\t'+body+'\n\tif (m_hActivator->pev->armorvalue < 100)')
        else: suffix=suffix.replace(needle,needle+'\n\t'+body,1)
        source=prefix+suffix
    (target.parent/f'{name}_generated.cpp').write_text('#include "../vitals.h"\n'+source)
