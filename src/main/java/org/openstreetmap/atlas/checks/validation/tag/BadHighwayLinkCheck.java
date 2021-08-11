package org.openstreetmap.atlas.checks.validation.tag;

import java.util.*;
import java.util.stream.Collectors;

import org.openstreetmap.atlas.checks.base.BaseCheck;
import org.openstreetmap.atlas.checks.flag.CheckFlag;
import org.openstreetmap.atlas.geography.atlas.items.AtlasObject;
import org.openstreetmap.atlas.geography.atlas.items.Edge;
import org.openstreetmap.atlas.geography.atlas.walker.OsmWayWalker;
import org.openstreetmap.atlas.tags.HighwayTag;
import org.openstreetmap.atlas.utilities.configuration.Configuration;

/**
 * Auto generated Check template
 *
 * @author v-naydinyan
 */
public class BadHighwayLinkCheck extends BaseCheck<Long>
{
    private static final long serialVersionUID = 6376650158676214570L;

    private static final List<String> HIGHWAY_LINKS_DEFAULT = Arrays.asList("motorway_link", "trunk_link",
            "primary_link", "secondary_link", "tertiary_link");
    private static final List<String> HIGHWAY_TYPES_PRIORITY_DEFAULT = Arrays.asList("motorway", "trunk", "primary",
            "secondary", "tertiary", "unclassified", "residential");
    private static final Map<String, String> HIGHWAY_TYPES_AND_LINKS_CORRESPONDENCE_DEFAULT = Map.of("motorway",
            "motorway_link", "trunk", "trunk_link", "primary", "primary_link", "secondary",
            "secondary_link", "tertiary", "tertiary_link");

    private static final String HIGHWAY_LINK_IS_NAMED_IMPROPERLY_INSTRUCTIONS = "";
    private static final String HIGHWAY_LINK_IS_TOO_LONG_INSTRUCTIONS = "";
    private static final String HIGHWAY_LINK_IS_TOO_LONG_AND_NAMED_IMPROPERLY = "";
    private static final List<String> FALLBACK_INSTRUCTIONS = Arrays.asList(HIGHWAY_LINK_IS_NAMED_IMPROPERLY_INSTRUCTIONS,
            HIGHWAY_LINK_IS_TOO_LONG_INSTRUCTIONS, HIGHWAY_LINK_IS_TOO_LONG_AND_NAMED_IMPROPERLY);

    private final List<String> highwayLinksTypesList;
    private final List<String> highwayTypesPriorityList;
    private final Map<String, String> highwayTypesAndLinksCorrespondence;


    /**
     * The default constructor that must be supplied. The Atlas Checks framework will generate the
     * checks with this constructor, supplying a configuration that can be used to adjust any
     * parameters that the check uses during operation.
     *
     * @param configuration
     *            the JSON configuration for this check
     */
    public BadHighwayLinkCheck(final Configuration configuration)
    {
        super(configuration);

        this.highwayLinksTypesList = this.configurationValue(configuration,
                "highwayTypes.highwayLinkTypes", HIGHWAY_LINKS_DEFAULT);
        this.highwayTypesPriorityList = this.configurationValue(configuration,
                "highwayTypes.highwayTypesPriorityList", HIGHWAY_TYPES_PRIORITY_DEFAULT);
        this.highwayTypesAndLinksCorrespondence = this.configurationValue(configuration,
                "highwayTypes.highwayLinkToHighwayTypeCorrespondence", HIGHWAY_TYPES_AND_LINKS_CORRESPONDENCE_DEFAULT);

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
        return !this.isFlagged(object.getOsmIdentifier())
                && (object instanceof Edge && ((Edge) object).isMainEdge())
                && highwayIsLink(object);
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

        final Edge edgeInQuestion = ((Edge) object).getMainEdge();
        final List<Edge> edges = new OsmWayWalker(edgeInQuestion).collectEdges().stream().sorted().collect(Collectors.toList());
        final List<Edge> inEdges = edges.get(0).start().connectedEdges().stream()
                .filter(inEdge -> inEdge.isMainEdge()
                        && inEdge.getOsmIdentifier() != edgeInQuestion.getOsmIdentifier()
                        && !inEdge.tag(HighwayTag.KEY).contains("_link"))
                .collect(Collectors.toList());

        List<String> inEdgesHighwayTags = inEdges.stream().map(edge -> edge.tag(HighwayTag.KEY)).collect(Collectors.toList());

        List<Integer> inEdgesHighwayIndexes = inEdgesHighwayTags.stream().map(tag -> this.highwayTypesPriorityList.indexOf(tag)).collect(Collectors.toList());

        Collections.sort(inEdgesHighwayIndexes);

        String highestPriorityHighway = this.highwayTypesPriorityList.get(inEdgesHighwayIndexes.get(0));




        final List<Edge> outEdges = edges.get(edges.size() - 1).end().connectedEdges().stream()
                .filter(outEdge -> outEdge.isMainEdge()
                        && outEdge.getOsmIdentifier() != edgeInQuestion.getOsmIdentifier()
                        && !outEdge.tag(HighwayTag.KEY).contains("_link"))
                .collect(Collectors.toList());

        String outHighestPriority = highestPriorityHighwayConnectingToTheLink(outEdges);
//
//        final List<String> outEdgesHighwayTags = outEdges.stream().map(edge -> edge.tag(HighwayTag.KEY)).collect(Collectors.toList());
//        final List<Integer> highwayIndeces = outEdgesHighwayTags.stream().map(tag -> this.highwayTypesPriorityList.indexOf(tag)).collect(Collectors.toList());
//
//        int lowestIndex = 100;
//        for (int ind : highwayIndeces)
//        {
//            if (ind < lowestIndex) {
//                lowestIndex = ind;
//            }
//        }
//
//        final String highestPriorityHighway = outEdgesHighwayTags.get(lowestIndex);






        if (highwayLinkLength(object) > 1000.00)
        {
            return Optional.of(this.createFlag(object, this.getLocalizedInstruction(0)));
        }
        return Optional.empty();
    }

    @Override
    protected List<String> getFallbackInstructions()
    {
        return FALLBACK_INSTRUCTIONS;
    }

    private boolean highwayIsLink(final AtlasObject object)
    {
        final String highwayTag = object.tag(HighwayTag.KEY);
        if (highwayTag != null && this.highwayLinksTypesList.contains(highwayTag.toLowerCase())){
            return true;
        }
        return false;
    }

    private double highwayLinkLength(final AtlasObject object) {
        double highwayLength = 0.0;
        final Set<Edge> edges = new OsmWayWalker((Edge) object).collectEdges();
        for(Edge edge : edges){
            highwayLength = highwayLength + edge.length().asMeters();
        }

        return highwayLength;
    }

    private List<String> highwayLinkConnectionsCheck(final AtlasObject object)
    {

        return null;
    }

    private String highwayStartingNodeHighwayConnections(final List<Edge> allEdgesOfWay, final Edge edgeAnalysed)
    {
        final List<Edge> inEdges = allEdgesOfWay.get(0).start().connectedEdges().stream()
                .filter(inEdge -> inEdge.isMainEdge()
                        && inEdge.getOsmIdentifier() != edgeAnalysed.getOsmIdentifier()
                        && !inEdge.tag(HighwayTag.KEY).contains("_link"))
                .collect(Collectors.toList());

        return null;
    }

    private String highwayEndingNodeHighwayConnections(final List<Edge> allEdgesOfWay)
    {
        return null;
    }

    private String highestPriorityHighwayConnectingToTheLink(final List<Edge> boundaryEdgesToCheckTheConnections)
    {
        if(boundaryEdgesToCheckTheConnections.size() > 0)
        {
            final List<String> highwayTagsOfConnectingEdges = boundaryEdgesToCheckTheConnections.stream().
                    map(connectingEdge -> connectingEdge.tag(HighwayTag.KEY)).collect(Collectors.toList());

            List<Integer> connectingEdgesHighwayPriorityIndex = highwayTagsOfConnectingEdges.stream()
                    .map(tag -> this.highwayTypesPriorityList.indexOf(tag.toLowerCase())).collect(Collectors.toList());

            Collections.sort(connectingEdgesHighwayPriorityIndex);

            final String highestPriorityHighwayTag = this.highwayTypesPriorityList.get(connectingEdgesHighwayPriorityIndex.get(0));

            return highestPriorityHighwayTag;
        }

        return null;
    }
}
