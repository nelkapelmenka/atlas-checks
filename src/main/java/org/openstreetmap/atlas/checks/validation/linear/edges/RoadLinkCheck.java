package org.openstreetmap.atlas.checks.validation.linear.edges;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.arrow.flatbuf.Bool;
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
    public static final double DISTANCE_MILES_DEFAULT = 1;
    private static final String INVALID_LINK_DISTANCE_INSTRUCTION = "Invalid link, distance, {0}, greater than maximum, {1}.";
    private static final String NO_SAME_CLASSIFICATION_INSTRUCTION = "None of the connected edges contain any edges with the same classification [{0}]";
    private static final String LINK_TOO_LONG_AND_CLASSIFICATION_WRONG = "";
    private static final List<String> FALLBACK_INSTRUCTIONS = Arrays
            .asList(INVALID_LINK_DISTANCE_INSTRUCTION, NO_SAME_CLASSIFICATION_INSTRUCTION, LINK_TOO_LONG_AND_CLASSIFICATION_WRONG);
    private static final long serialVersionUID = 6828331285027997648L;
    private final Distance maximumLength;

    public RoadLinkCheck(final Configuration configuration)
    {
        super(configuration);
        this.maximumLength = configurationValue(configuration, "length.maximum.miles",
                DISTANCE_MILES_DEFAULT, Distance::miles);
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
            return Optional.of(this.createFlag(object, this.getLocalizedInstruction(0)));
        }

        else if(!(linkLength == null)
                && linkLength.isGreaterThan(this.maximumLength))
        {
            return Optional.of(this.createFlag(object, this.getLocalizedInstruction(0)));
        }

        else if(!(suggestedLinkName == null))
        {
            return Optional.of(this.createFlag(object, this.getLocalizedInstruction(0)));
        }

        return Optional.empty();

//        if (linkLength(object).isGreaterThan(this.maximumLength))
//        {
//            return Optional.of(this.createFlag(new OsmWayWalker(edge).collectEdges(),
//                    this.getLocalizedInstruction(0, edge.length(), this.maximumLength)));
//        }
//        else if (edge.connectedEdges().stream().filter(Edge::isMainEdge).noneMatch(
//                connected -> connected.highwayTag().isOfEqualClassification(edge.highwayTag())))
//        {
//            final Set<AtlasObject> geometry = new HashSet<>();
//            geometry.add(edge);
//            geometry.addAll(edge.connectedEdges().stream().filter(Edge::isMainEdge)
//                    .collect(Collectors.toSet()));
//            final Set<Edge> flagEdges = geometry.stream()
//                    .flatMap(obj -> new OsmWayWalker((Edge) obj).collectEdges().stream())
//                    .collect(Collectors.toSet());
//            final CheckFlag flag = this.createFlag(flagEdges,
//                    this.getLocalizedInstruction(1, edge.highwayTag().toString()));
//            flag.addPoint(edge.start().getLocation().midPoint(edge.end().getLocation()));
//            return Optional.of(flag);
//        }
//        return Optional.empty();
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
        Distance highwayLength = null;
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
        final Edge startingEdge = startingNodeHighwayConnection(currentWay, currentEdge);
        final Edge endingEdge = endingNodeHighwayConnection(currentWay, currentEdge);

        String suggestedLinkName = null;
        if((startingEdge.highwayTag().isMoreImportantThanOrEqualTo(endingEdge.highwayTag()))
                && !startingEdge.highwayTag().isOfEqualClassification(currentEdge.highwayTag()))
        {
            suggestedLinkName = startingEdge.highwayTag().getLinkFromHighway().toString();
        }
        else if(endingEdge.highwayTag().isMoreImportantThan(startingEdge.highwayTag())
                && !endingEdge.highwayTag().isOfEqualClassification(currentEdge.highwayTag()))
        {
            suggestedLinkName = endingEdge.highwayTag().getLinkFromHighway().toString();
        }

        return suggestedLinkName;
    }

    private Edge startingNodeHighwayConnection(final List<Edge> allEdgesOfLink, final Edge currentEdge)
    {
        final List<Edge> startConnections = allEdgesOfLink.get(0).start().connectedEdges().stream()
                .filter(connection -> connection.isMainEdge()
                        && connection.getOsmIdentifier() != currentEdge.getOsmIdentifier()
                        && !connection.highwayTag().isLink())
                .collect(Collectors.toList());

        Edge highestStartPriorityEdge = startConnections.get(0);
        for (int i=0; i < startConnections.size()-1; i++)
        {
            if(highestStartPriorityEdge.highwayTag().isLessImportantThan(startConnections.get(i).highwayTag()))
            {
                highestStartPriorityEdge = startConnections.get(i);
            }
        }

        return highestStartPriorityEdge;
    }

    private Edge endingNodeHighwayConnection(final List<Edge> allEdgesOfLink, final Edge currentEdge)
    {
        final List<Edge> endConnections = allEdgesOfLink.get(0).end().connectedEdges().stream()
                .filter(connection -> connection.isMainEdge()
                        && connection.getOsmIdentifier() != currentEdge.getOsmIdentifier()
                        && !connection.highwayTag().isLink())
                .collect(Collectors.toList());

        Edge highestEndPriorityEdge = endConnections.get(0);
        for (int i=0; i < endConnections.size()-1; i++)
        {
            if(highestEndPriorityEdge.highwayTag().isLessImportantThan(endConnections.get(i).highwayTag()))
            {
                highestEndPriorityEdge = endConnections.get(i);
            }
        }

        return highestEndPriorityEdge;
    }
}
