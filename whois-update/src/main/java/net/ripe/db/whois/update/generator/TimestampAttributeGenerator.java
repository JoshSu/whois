package net.ripe.db.whois.update.generator;

import net.ripe.db.whois.common.DateTimeProvider;
import net.ripe.db.whois.common.rpsl.AttributeType;
import net.ripe.db.whois.common.rpsl.RpslAttribute;
import net.ripe.db.whois.common.rpsl.RpslObject;
import net.ripe.db.whois.common.rpsl.RpslObjectBuilder;
import net.ripe.db.whois.common.rpsl.TimestampsMode;
import net.ripe.db.whois.common.rpsl.ValidationMessages;
import net.ripe.db.whois.update.domain.Action;
import net.ripe.db.whois.update.domain.Update;
import net.ripe.db.whois.update.domain.UpdateContext;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TimestampAttributeGenerator extends AttributeGenerator {
    private final DateTimeProvider dateTimeProvider;
    private final TimestampsMode timestampsMode;

    @Autowired
    TimestampAttributeGenerator(final DateTimeProvider dateTimeProvider, final TimestampsMode timestampsMode) {
        this.dateTimeProvider = dateTimeProvider;
        this.timestampsMode = timestampsMode;
    }

    @Override
    public RpslObject generateAttributes(final RpslObject originalObject, final RpslObject updatedObject, final Update update, final UpdateContext updateContext) {
        RpslObject resultObject = updatedObject;

        //TODO TP : remove when timestamps always on
        if (! timestampsMode.isTimestampsOff()) {

            final Action action = updateContext.getAction(update);
            if (action == Action.CREATE || action == Action.MODIFY || action == Action.DELETE) {
                final RpslObjectBuilder builder = new RpslObjectBuilder(updatedObject);

                boolean addWarningsFlag = updateContext.getPreparedUpdate(update).getOverrideOptions().isSkipLastModified() ? false : true;

                generateTimestampAttributes(builder, originalObject, updatedObject, update, updateContext, addWarningsFlag);
                resultObject = builder.get();
            }
        }

        return resultObject;
    }

    private void generateTimestampAttributes(final RpslObjectBuilder builder, final RpslObject originalObject, final RpslObject updatedObject, final Update update, final UpdateContext updateContext, final boolean addWarningsFlag) {
        final Action action = updateContext.getAction(update);

        final DateTime now = dateTimeProvider.getCurrentDateTimeUtc();
        final String nowString = now.toString(ISODateTimeFormat.dateTimeNoMillis().withZone(DateTimeZone.UTC));

        RpslAttribute generatedCreatedAttribute = null;
        RpslAttribute generatedLastModifiedAttribute = null;

        switch (action) {
            case CREATE:
                generatedCreatedAttribute = new RpslAttribute(AttributeType.CREATED, nowString);
                generatedLastModifiedAttribute = new RpslAttribute(AttributeType.LAST_MODIFIED, nowString);
                break;

            case MODIFY:
                if (originalObject.containsAttribute(AttributeType.CREATED)) {
                    generatedCreatedAttribute = new RpslAttribute(AttributeType.CREATED, originalObject.getValueForAttribute(AttributeType.CREATED));
                }

                if (updateContext.getPreparedUpdate(update).getOverrideOptions().isSkipLastModified()) {
                    if (originalObject.containsAttribute(AttributeType.LAST_MODIFIED)) {
                        generatedLastModifiedAttribute = new RpslAttribute(AttributeType.LAST_MODIFIED, originalObject.getValueForAttribute(AttributeType.LAST_MODIFIED));
                    }
                } else {
                    generatedLastModifiedAttribute = new RpslAttribute(AttributeType.LAST_MODIFIED, nowString);
                }
                break;

            case DELETE:
                // for delete we just ignore what was passed in and make sure object looks like stored version
                if (originalObject.containsAttribute(AttributeType.CREATED)) {
                    generatedCreatedAttribute = new RpslAttribute(AttributeType.CREATED, originalObject.getValueForAttribute(AttributeType.CREATED));
                }
                if (originalObject.containsAttribute(AttributeType.LAST_MODIFIED)) {
                    generatedLastModifiedAttribute = new RpslAttribute(AttributeType.LAST_MODIFIED, originalObject.getValueForAttribute(AttributeType.LAST_MODIFIED));
                }
                break;

            case NOOP:
                break;

        }

        warnAndAdd(update, updateContext, builder, updatedObject, AttributeType.CREATED, generatedCreatedAttribute, addWarningsFlag);
        warnAndAdd(update, updateContext, builder, updatedObject, AttributeType.LAST_MODIFIED, generatedLastModifiedAttribute, addWarningsFlag);

    }

    private void warnAndAdd(final Update update, final UpdateContext updateContext, final RpslObjectBuilder builder, final RpslObject updatedObject, final AttributeType attributeType, final RpslAttribute generatedAttribute, final boolean addWarningsFlag) {
        builder.removeAttributeType(attributeType);
        if (generatedAttribute != null) {
            builder.addAttributeSorted(generatedAttribute);
        }

        if (addWarningsFlag == true) {
            final RpslAttribute inputAttribute = updatedObject.containsAttribute(attributeType) ? updatedObject.findAttribute(attributeType) : null;
            if (inputAttribute != null && !inputAttribute.equals(generatedAttribute)) {
                if (generatedAttribute != null) {
                    updateContext.addMessage(update, generatedAttribute, ValidationMessages.suppliedAttributeReplacedWithGeneratedValue(attributeType));
                } else {
                    updateContext.addMessage(update, ValidationMessages.suppliedAttributeReplacedWithGeneratedValue(attributeType));
                }
            }
        }
    }

}
