package cz.coffeerequired.skript.core.bukkit;

import ch.njol.skript.Skript;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.expressions.base.EventValueExpression;
import ch.njol.skript.lang.ExpressionType;

import java.util.UUID;

@Name("Watcher event value-expression UUID")
@Description("value-expression for getting uuid from current watcher event")
@Since("2.9")
public class EvtUUID extends EventValueExpression<UUID> {
    static {
        Skript.registerExpression(EvtUUID.class, UUID.class, ExpressionType.SIMPLE, "[the] [event-](uuid|id)");
    }

    public EvtUUID() {
        super(UUID.class);
    }
}