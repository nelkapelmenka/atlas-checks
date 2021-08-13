package org.openstreetmap.atlas.checks.validation.linear.edges;

import java.util.*;
import java.util.stream.Collectors;

import org.openstreetmap.atlas.checks.base.BaseCheck;
import org.openstreetmap.atlas.checks.flag.CheckFlag;
import org.openstreetmap.atlas.geography.atlas.items.AtlasObject;
import org.openstreetmap.atlas.geography.atlas.items.Edge;
import org.openstreetmap.atlas.geography.atlas.walker.OsmWayWalker;
import org.openstreetmap.atlas.tags.HighwayTag;
import org.openstreetmap.atlas.utilities.configuration.Configuration;
import org.openstreetmap.atlas.utilities.scalars.Distance;

/**
 * Verify that one end or the other is a fork to/from a road of the same class, that is not a _link
 *
 * @author cuthbertm
 */
public class RoadLinkCheck extends BaseCheck<Long>
{

    private final String noConnectionsWithLinkEquivalent = "The link has no connections on either end that have link equivalent";
    private final String noConnectionOnEitherSide = "The link has no proper highway connection on either side";
    public static final double DISTANCE_METERS_DEFAULT = 1000;
    private static final String INVALID_LINK_DISTANCE_INSTRUCTION = "Invalid link, distance, {0}, greater than maximum, {1}.";
    private static final String NO_SAME_CLASSIFICATION_INSTRUCTION =
            "The link has wrong classification based on the surrounding highways and the classification priorities. A suggested highway tag would be {0}.";
    private static final String LINK_TOO_LONG_AND_CLASSIFICATION_WRONG_INSTRUCTION =
            "The link, distance, {0} is greater than max {1} AND it has the wrong classification. Check if the highway type is still a link and if so, a suggested link highway tag is {2}";
    private static final String LINK_HAS_NO_CONNECTION_INSTRUCTION =
            "The link has no connection to highway on one of the sides. Generally highway links have an entrance and an exist, please confirm if this is a link.";
    private static final String LINK_HAS_NO_CONNECTIONS_WITH_LINK_EQUIVALENT_INSTRUCTION =
            "The link has no connections with link equivalent. Please confirm if the highway is supposed to be a link.";
    private static final List<String> FALLBACK_INSTRUCTIONS = Arrays
            .asList(INVALID_LINK_DISTANCE_INSTRUCTION, NO_SAME_CLASSIFICATION_INSTRUCTION, LINK_TOO_LONG_AND_CLASSIFICATION_WRONG_INSTRUCTION,
                    LINK_HAS_NO_CONNECTION_INSTRUCTION, LINK_HAS_NO_CONNECTIONS_WITH_LINK_EQUIVALENT_INSTRUCTION);
    private static final long serialVersionUID = 6828331285027997648L;
    private final Distance maximumLength;

    public RoadLinkCheck(final Configuration configuration)
    {
        super(configuration);
        this.maximumLength = configurationValue(configuration, "length.maximum.meters",
                DISTANCE_METERS_DEFAULT, Distance::meters);
    }

    @Override
    public boolean validCheckForObject(final AtlasObject object)
    {
        return object instanceof Edge && ((Edge) object).highwayTag().isLink()
                && ((Edge) object).isMainEdge() && !this.isFlagged(object.getOsmIdentifier());
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

    @Override
    protected Optional<CheckFlag> flag(final AtlasObject object)
    {
        final Edge edge = (Edge) object;
        this.markAsFlagged(edge.getOsmIdentifier());

        final Distance linkLength = linkLength(object);
        final String suggestedLinkName = linkProperConnectionNameSuggestion(object);

        if(!(linkLength == null)
                && linkLength.isGreaterThan(this.maximumLength)
                && !(suggestedLinkName == null)) {
            return Optional.of(this.createFlag(object, this.getLocalizedInstruction(2, linkLength.asMeters(), this.maximumLength, suggestedLinkName)));
        }

        else if(!(linkLength == null)
                && linkLength.isGreaterThan(this.maximumLength))
        {
            return Optional.of(this.createFlag(object, this.getLocalizedInstruction(0, linkLength.asMeters(), this.maximumLength)));
        }

        else if(!(suggestedLinkName == null) && suggestedLinkName == this.noConnectionOnEitherSide)
        {
            return Optional.of(this.createFlag(object, this.getLocalizedInstruction(3)));
        }

        else if(suggestedLinkName != null && suggestedLinkName == this.noConnectionsWithLinkEquivalent)
        {
            return Optional.of(this.createFlag(object, this.getLocalizedInstruction(4)));
        }

        else if(!(suggestedLinkName == null))
        {
            return Optional.of(this.createFlag(object, this.getLocalizedInstruction(1, suggestedLinkName.toLowerCase())));
        }

        return Optional.empty();
    }

    @Override
    protected List<String> getFallbackInstructions()
    {
        return FALLBACK_INSTRUCTIONS;
    }

    /**
     *
     * @param object
     * @return
     */
    private Distance linkLength(final AtlasObject object)
    {
        Distance highwayLength = Distance.ZERO;
        final Set<Edge> highwayLink = new OsmWayWalker((Edge) object).collectEdges();
        for (Edge edge : highwayLink)
        {
            highwayLength.add(edge.length());
        }

        return highwayLength;
    }

    private String linkProperConnectionNameSuggestion(final AtlasObject object)
    {
        final Edge currentEdge = ((Edge) object).getMainEdge();
        final List<Edge> currentWay = new OsmWayWalker(currentEdge).collectEdges().stream()
                .sorted().collect(Collectors.toList());
        final HighwayTag startingEdgeTag = startingNodeHighwayConnection(currentWay, currentEdge);
        final HighwayTag endingEdgeTag = endingNodeHighwayConnection(currentWay, currentEdge);
        final HighwayTag currentEdgeTag = currentEdge.highwayTag();

        String suggestedLinkName = null;

        if (startingEdgeTag == null || endingEdgeTag == null)
        {
            suggestedLinkName = this.noConnectionOnEitherSide;
        }

        else if(!startingEdgeTag.canHaveLink() && !endingEdgeTag.canHaveLink())
        {
            suggestedLinkName = this.noConnectionsWithLinkEquivalent;
        }

        else if((startingEdgeTag.isOfEqualClassification(endingEdgeTag))
                && !startingEdgeTag.isOfEqualClassification(currentEdgeTag))
        {
            suggestedLinkName = startingEdgeTag.getLinkFromHighway().get().toString();
        }
        else if((startingEdgeTag.isMoreImportantThan(endingEdgeTag))
                && !startingEdgeTag.isOfEqualClassification(currentEdgeTag))
        {
            suggestedLinkName = startingEdgeTag.getLinkFromHighway().get().toString();
        }
        else if(endingEdgeTag.isMoreImportantThan(startingEdgeTag)
                && !endingEdgeTag.isOfEqualClassification(currentEdgeTag))
        {
            suggestedLinkName = endingEdgeTag.getLinkFromHighway().get().toString();
        }

        return suggestedLinkName;
    }

    private HighwayTag startingNodeHighwayConnection(final List<Edge> allEdgesOfLink, final Edge currentEdge)
    {
        final List<Edge> startConnections = allEdgesOfLink.get(0).start().connectedEdges().stream()
                .filter(connection -> connection.isMainEdge()
                        && connection.getOsmIdentifier() != currentEdge.getOsmIdentifier()
                        && !connection.highwayTag().isLink())
                .collect(Collectors.toList());

        if (startConnections.isEmpty()){
            return null;
        }

        HighwayTag startHighestPriorityTag = HighwayTag.NO;
        for (Edge startConnectionsEdge : startConnections)
        {
            if(startHighestPriorityTag.isLessImportantThan(startConnectionsEdge.highwayTag()))
            {
                startHighestPriorityTag = startConnectionsEdge.highwayTag();
            }
        }

        return startHighestPriorityTag;
    }

    private HighwayTag endingNodeHighwayConnection(final List<Edge> allEdgesOfLink, final Edge currentEdge)
    {
        final List<Edge> endConnections = allEdgesOfLink.get(0).end().connectedEdges().stream()
                .filter(connection -> connection.isMainEdge()
                        && connection.getOsmIdentifier() != currentEdge.getOsmIdentifier()
                        && !connection.highwayTag().isLink())
                .collect(Collectors.toList());

        if(endConnections.isEmpty())
        {
            return null;
        }

        HighwayTag endHighestPriorityTag = HighwayTag.NO;
        for (Edge endConnectionsEdge : endConnections)
        {
            if(endHighestPriorityTag.isLessImportantThan(endConnectionsEdge.highwayTag()))
            {
                endHighestPriorityTag = endConnectionsEdge.highwayTag();
            }
        }

        return endHighestPriorityTag;
    }
}
