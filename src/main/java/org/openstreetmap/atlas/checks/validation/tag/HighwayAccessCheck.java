package org.openstreetmap.atlas.checks.validation.tag;

import java.util.*;

import org.openstreetmap.atlas.checks.base.BaseCheck;
import org.openstreetmap.atlas.checks.flag.CheckFlag;
import org.openstreetmap.atlas.geography.atlas.items.AtlasObject;
import org.openstreetmap.atlas.geography.atlas.items.Edge;
import org.openstreetmap.atlas.geography.atlas.walker.OsmWayWalker;
import org.openstreetmap.atlas.tags.AccessTag;
import org.openstreetmap.atlas.tags.HighwayTag;
import org.openstreetmap.atlas.utilities.configuration.Configuration;

/**
 * HighwayAccessCheck looks for ways that contain the access tag "yes" or "permissive". If the access tag is found, then
 * the highway tag is also checked. Finally, the object is flagged if the highway tag is found in either motorway tags
 * or in the footway tags provided in the beginning.
 *
 * @author v-naydinyan
 */
public class HighwayAccessCheck extends BaseCheck<Long>
{

    private static final long serialVersionUID = -5533238262833368666L;
    private static final List<String> AccessTagsToFlag = Arrays.asList("yes", "permissive");
    private static final List<String> MotorwayHighwayTags = Arrays.asList("motorway", "trunk");
    private static final List<String> FootwayHighwayTags = Arrays.asList("footway", "bridleway", "steps", "path",
            "cycleway", "pedestrian", "track", "bus_guideway", "busway", "raceway");

    private static final String HIGHWAY_IS_MOTORWAY_INSTRUCTION =
            "Including ski, horse, moped, hazmat and so on, unless explicitly excluded.";
    private static final String HIGHWAY_IS_FOOTWAY_INSTRUCTION =
            "Including car, horse, moped, hazmat and so on, unless explicitly excluded.";
    private static final List<String> FALLBACK_INSTRUCTIONS = Arrays.asList(HIGHWAY_IS_MOTORWAY_INSTRUCTION,
            HIGHWAY_IS_FOOTWAY_INSTRUCTION);
    /**
     * The default constructor that must be supplied. The Atlas Checks framework will generate the
     * checks with this constructor, supplying a configuration that can be used to adjust any
     * parameters that the check uses during operation.
     *
     * There are no internal variables
     *
     * @param configuration
     *            the JSON configuration for this check
     */
    public HighwayAccessCheck(final Configuration configuration)
    {
        super(configuration);
    }

    /**
     * This function will validate if the supplied atlas object is valid for the check.
     *
     * @param object
     *            the atlas object supplied by the Atlas-Checks framework for evaluation
     * @return {@code true} if this object should be checked
     */
    @Override
    public boolean validCheckForObject(final AtlasObject object)
    {
        // by default we will assume all objects as valid
        return (!this.isFlagged(object.getOsmIdentifier()) && (object instanceof Edge && ((Edge) object).isMainEdge()));
    }

    @Override
    protected CheckFlag createFlag(final AtlasObject object, final String instruction)
    {
        if (object instanceof Edge)
        {
            return super.createFlag(new OsmWayWalker((Edge) object).collectEdges(), instruction);
        }
        return super.createFlag(object, instruction);
    }

    /**
     * This is the actual function that will check to see whether the object needs to be flagged.
     *
     * @param object
     *            the atlas object supplied by the Atlas-Checks framework for evaluation
     * @return an optional {@link CheckFlag} object that
     */
    @Override
    protected Optional<CheckFlag> flag(final AtlasObject object)
    {
        this.markAsFlagged(object.getOsmIdentifier());

        String accessTag = object.tag(AccessTag.KEY);
        String highwayTag = object.tag(HighwayTag.KEY);

        //Check is the access tag is yes or permissive
        if (AccessTagsToFlag.contains(accessTag))
        {
            //Checks if the highway tag is in the motorway tags provided above
            if (MotorwayHighwayTags.contains(highwayTag))
            {
                return Optional.of(this.createFlag(object, this.getLocalizedInstruction(0)));
            }
            //Checks if the highway tag is in the footway tags provided above
            if (FootwayHighwayTags.contains(highwayTag))
            {
                return Optional.of(this.createFlag(object, this.getLocalizedInstruction(1)));
            }
        }
        return Optional.empty();
    }

    @Override
    protected List<String> getFallbackInstructions() { return FALLBACK_INSTRUCTIONS; }

}