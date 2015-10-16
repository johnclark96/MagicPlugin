package com.elmakers.mine.bukkit.action;

import com.elmakers.mine.bukkit.api.action.CastContext;
import com.elmakers.mine.bukkit.api.spell.Spell;
import com.elmakers.mine.bukkit.api.spell.SpellResult;
import com.elmakers.mine.bukkit.spell.BaseSpell;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public abstract class CompoundEntityAction extends CompoundAction
{
    private List<WeakReference<Entity>> entities = new ArrayList<WeakReference<Entity>>();
    private int currentEntity = 0;

    public abstract void addEntities(CastContext context, List<WeakReference<Entity>> entities);

    @Override
    public void reset(CastContext context)
    {
        super.reset(context);
        currentEntity = 0;
    }

	@Override
    public SpellResult perform(CastContext context)
	{
        if (currentEntity == 0)
        {
            entities.clear();
            addEntities(context, entities);
        }

        SpellResult result = SpellResult.NO_TARGET;
        while (currentEntity < entities.size())
        {
            Entity entity = entities.get(currentEntity).get();
            if (entity == null)
            {
                currentEntity++;
                skippedActions(context);
                continue;
            }
            actionContext.setTargetEntity(entity);
            actionContext.setTargetLocation(entity.getLocation());
            SpellResult entityResult = super.perform(actionContext);
            result = result.min(entityResult);
            if (entityResult.isStop()) {
                break;
            }
            currentEntity++;
            if (currentEntity < entities.size())
            {
                super.reset(context);
            }
        }

		return result;
	}

    @Override
    public Object clone()
    {
        CompoundEntityAction action = (CompoundEntityAction)super.clone();
        if (action != null) {
            action.entities = new ArrayList<WeakReference<Entity>>(this.entities);
        }
        return action;
    }
}
