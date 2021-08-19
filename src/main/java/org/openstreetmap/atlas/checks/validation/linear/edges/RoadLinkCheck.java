package org.openstreetmap.atlas.checks.validation.linear.edges;

import java.util.*;
import java.util.stream.Collectors;

import org.openstreetmap.atlas.checks.base.BaseCheck;
import org.openstreetmap.atlas.checks.flag.CheckFlag;
import org.openstreetmap.atlas.geography.Heading;
import org.openstreetmap.atlas.geography.atlas.items.AtlasObject;
import org.openstreetmap.atlas.geography.atlas.items.Edge;
import org.openstreetmap.atlas.geography.atlas.walker.OsmWayWalker;
import org.openstreetmap.atlas.tags.HighwayTag;
import org.openstreetmap.atlas.utilities.configuration.Configuration;
import org.openstreetmap.atlas.utilities.scalars.Angle;
import org.openstreetmap.atlas.utilities.scalars.Distance;

/**
 * Verify that one end or the other is a fork to/from a road of the same class, that is not a _link
 *
 * @author cuthbertm
 */
public class RoadLinkCheck extends BaseCheck<Long>
{

    private final String noConnectionsWithLinkEquivalent = "has no connections on either end that have link equivalent";
    private final String noConnectionOnBothSides = "has no proper highway connection on either side";
    public static final double DISTANCE_METERS_DEFAULT = 1000;
    private static final String INVALID_LINK_DISTANCE_INSTRUCTION = "Invalid link, distance {0} km, greater than maximum, {1}.";
    private static final String NO_SAME_CLASSIFICATION_INSTRUCTION =
            "The link has wrong classification based on the surrounding highways and the classification priorities. A suggested highway tag would be {0}.";
    private static final String LINK_TOO_LONG_AND_CLASSIFICATION_WRONG_INSTRUCTION =
            "The link, distance {0} km is greater than max {1} AND it has the wrong classification. Check if the highway type is still a link and if so, change classification.";
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

        if(linkLength.isGreaterThan(this.maximumLength)
                && suggestedLinkName != null) {
            return Optional.of(this.createFlag(object, this.getLocalizedInstruction(2, linkLength.asKilometers(), this.maximumLength)));
        }

        else if(linkLength.isGreaterThan(this.maximumLength))
        {
            return Optional.of(this.createFlag(object, this.getLocalizedInstruction(0, linkLength.asKilometers(), this.maximumLength)));
        }

        else if((suggestedLinkName != null) && suggestedLinkName.equals(this.noConnectionOnBothSides))
        {
            return Optional.of(this.createFlag(object, this.getLocalizedInstruction(3)));
        }

        else if((suggestedLinkName != null) && suggestedLinkName.equals(this.noConnectionsWithLinkEquivalent))
        {
            return Optional.of(this.createFlag(object, this.getLocalizedInstruction(4)));
        }

        else if(suggestedLinkName != null)
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
        Distance highwayLength = Distance.meters(0);
        final Set<Edge> highwayLink = new OsmWayWalker((Edge) object).collectEdges();
        for (Edge edge : highwayLink)
        {
            highwayLength = highwayLength.add(edge.length());
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

        if (startingEdgeTag == HighwayTag.NO && endingEdgeTag == HighwayTag.NO)
        {
            suggestedLinkName = this.noConnectionOnBothSides;
        }

        else if((!startingEdgeTag.canHaveLink() && !startingEdgeTag.isLink())
                && (!endingEdgeTag.canHaveLink() && !endingEdgeTag.isLink()))
        {
            suggestedLinkName = this.noConnectionsWithLinkEquivalent;
        }

        else if((startingEdgeTag.isOfEqualClassification(endingEdgeTag))
                && !startingEdgeTag.isOfEqualClassification(currentEdgeTag))
        {
            if (startingEdgeTag.isLink())
            {
                suggestedLinkName = startingEdgeTag.getTagValue();
            }
            else
            {
                suggestedLinkName = startingEdgeTag.getLinkFromHighway().get().toString();
            }
        }
        else if(startingEdgeTag.isMoreImportantThan(endingEdgeTag)
                && !startingEdgeTag.isOfEqualClassification(currentEdgeTag))
        {
            if (startingEdgeTag.isLink())
            {
                suggestedLinkName = startingEdgeTag.getTagValue();
            }
            else
            {
                suggestedLinkName = startingEdgeTag.getLinkFromHighway().get().toString();
            }
        }
        else if(endingEdgeTag.isMoreImportantThan(startingEdgeTag)
                && !endingEdgeTag.isOfEqualClassification(currentEdgeTag))
        {
            if (endingEdgeTag.isLink())
            {
                suggestedLinkName = endingEdgeTag.getTagValue();
            }
            else
            {
                suggestedLinkName = endingEdgeTag.getLinkFromHighway().get().toString();
            }
        }

        return suggestedLinkName;
    }

    private HighwayTag startingNodeHighwayConnection(final List<Edge> allEdgesOfLink, final Edge currentEdge)
    {
        List<Edge> startConnections = allEdgesOfLink.get(0).start().inEdges().stream()
                .filter(connection -> connection.getOsmIdentifier() != currentEdge.getOsmIdentifier())
                .collect(Collectors.toList());

//        if (startConnections.isEmpty()){
//            startConnections = allEdgesOfLink.get(0).start().inEdges().stream()
//                    .filter(connection -> connection.isMainEdge()
//                            && connection.getOsmIdentifier() != currentEdge.getOsmIdentifier())
//                    .collect(Collectors.toList());
//        }

        final Angle angleDifferenceSmallest = Angle.degrees(180);
        Angle smallestAngle;
        int indexOfClosestAngles = 0;

        if (startConnections.size() > 1)
        {
            List<Angle> startConnectionAngles = startConnections.stream()
                    .map(connection -> angleDifferenceSmallest.difference(angleDifferenceBetweenEdges(currentEdge, connection)))
                    .collect(Collectors.toList());


            for (Angle angle : startConnectionAngles)
            {
                if (angle.isLessThan(angleDifferenceSmallest) && angle.isGreaterThan(Angle.degrees(-5)))
                {
                    smallestAngle = angle;
                    indexOfClosestAngles = startConnectionAngles.indexOf(angle);
                }
            }


        }
        Edge startConnection = startConnections.get(indexOfClosestAngles);

//        HighwayTag startHighestPriorityTag = HighwayTag.NO;
//        for (Edge startConnectionsEdge : startConnections)
//        {
//            if(startHighestPriorityTag.isLessImportantThan(startConnectionsEdge.highwayTag()))
//            {
//                startHighestPriorityTag = startConnectionsEdge.highwayTag();
//            }
//        }

        return startConnection.highwayTag();
    }

    private HighwayTag endingNodeHighwayConnection(final List<Edge> allEdgesOfLink, final Edge currentEdge)
    {
        List<Edge> endConnections = allEdgesOfLink.get(allEdgesOfLink.size()-1).end().outEdges().stream()
                .filter(connection ->                                       connection.getOsmIdentifier() != currentEdge.getOsmIdentifier())
                .collect(Collectors.toList());

//        if(endConnections.isEmpty())
//        {
//            endConnections = allEdgesOfLink.get(allEdgesOfLink.size()-1).end().outEdges().stream()
//                    .filter(connection -> connection.isMainEdge()
//                            && connection.getOsmIdentifier() != currentEdge.getOsmIdentifier())
//                    .collect(Collectors.toList());
//        }


        final Angle angleDifferenceSmallest = Angle.degrees(180);
        Angle smallestAngle = Angle.degrees(360);
        int indexOfClosestAngles = 0;

        if (endConnections.size() > 1)
        {
            List<Angle> endConnectionAngles = endConnections.stream()
                    .map(connection -> angleDifferenceSmallest.difference(angleDifferenceBetweenEdges(currentEdge, connection)))
                    .collect(Collectors.toList());


            for (Angle angle : endConnectionAngles)
            {
                if (angle.isLessThan(smallestAngle) && angle.isGreaterThan(Angle.degrees(-5)))
                {
                    smallestAngle = angle;
                    indexOfClosestAngles = endConnectionAngles.indexOf(angle);
                }
            }
        }
        Edge endConnection = endConnections.get(indexOfClosestAngles);

//        HighwayTag endHighestPriorityTag = HighwayTag.NO;
//        for (Edge endConnectionsEdge : endConnections)
//        {
//            if(endHighestPriorityTag.isLessImportantThan(endConnectionsEdge.highwayTag()))
//            {
//                endHighestPriorityTag = endConnectionsEdge.highwayTag();
//            }
//        }

        return endConnection.highwayTag();
    }

    private Angle angleDifferenceBetweenEdges(Edge edge1, Edge edge2)
    {
        Optional<Heading> edge1heading;
        Optional<Heading> edge2heading;

        edge1heading = edge1.asPolyLine().finalHeading();
        edge2heading = edge2.asPolyLine().initialHeading();

        if(edge1heading.isPresent() && edge2heading.isPresent())
        {
            return edge1heading.get().difference(edge2heading.get());
        }

        return Angle.NONE;
    }
}
