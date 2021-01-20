/*
 * Copyright (C) 2013 - 2020 Oracle and/or its affiliates. All rights reserved.
 */
package oracle.pgql.lang;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.spoofax.interpreter.terms.IStrategoAppl;
import org.spoofax.interpreter.terms.IStrategoTerm;

import oracle.pgql.lang.ir.CommonPathExpression;
import oracle.pgql.lang.ir.Direction;
import oracle.pgql.lang.ir.ExpAsVar;
import oracle.pgql.lang.ir.GraphPattern;
import oracle.pgql.lang.ir.GraphQuery;
import oracle.pgql.lang.ir.GroupBy;
import oracle.pgql.lang.ir.OrderBy;
import oracle.pgql.lang.ir.OrderByElem;
import oracle.pgql.lang.ir.PathFindingGoal;
import oracle.pgql.lang.ir.Projection;
import oracle.pgql.lang.ir.QueryEdge;
import oracle.pgql.lang.ir.QueryExpression;
import oracle.pgql.lang.ir.QueryExpression.LogicalExpression.And;
import oracle.pgql.lang.ir.QueryExpression.PropertyAccess;
import oracle.pgql.lang.ir.QueryExpression.Constant.ConstString;
import oracle.pgql.lang.ir.QueryExpression.ExpressionType;
import oracle.pgql.lang.ir.QueryExpression.VarRef;
import oracle.pgql.lang.ir.QueryPath;
import oracle.pgql.lang.ir.QueryVariable;
import oracle.pgql.lang.ir.QueryVariable.VariableType;
import oracle.pgql.lang.ir.QueryVertex;
import oracle.pgql.lang.ir.SchemaQualifiedName;
import oracle.pgql.lang.ir.SelectQuery;
import oracle.pgql.lang.ir.PgqlStatement;
import oracle.pgql.lang.ir.VertexPairConnection;
import oracle.pgql.lang.ir.modify.DeleteClause;
import oracle.pgql.lang.ir.modify.EdgeInsertion;
import oracle.pgql.lang.ir.modify.InsertClause;
import oracle.pgql.lang.ir.modify.Insertion;
import oracle.pgql.lang.ir.modify.Modification;
import oracle.pgql.lang.ir.modify.ModifyQuery;
import oracle.pgql.lang.ir.modify.SetPropertyExpression;
import oracle.pgql.lang.ir.modify.Update;
import oracle.pgql.lang.ir.modify.UpdateClause;
import oracle.pgql.lang.ir.modify.VertexInsertion;

import static oracle.pgql.lang.TranslateCreateExternalSchema.translateCreateExternalSchema;
import static oracle.pgql.lang.TranslateDropExternalSchema.translateDropExternalSchema;
import static oracle.pgql.lang.TranslateCreatePropertyGraph.translateCreatePropertyGraph;
import static oracle.pgql.lang.TranslateDropPropertyGraph.translateDropPropertyGraph;
import static oracle.pgql.lang.CommonTranslationUtil.getString;
import static oracle.pgql.lang.CommonTranslationUtil.getList;
import static oracle.pgql.lang.CommonTranslationUtil.getSchemaQualifiedName;
import static oracle.pgql.lang.CommonTranslationUtil.isNone;
import static oracle.pgql.lang.CommonTranslationUtil.isSome;
import static oracle.pgql.lang.CommonTranslationUtil.getSome;
import static oracle.pgql.lang.CommonTranslationUtil.getConstructorName;
import static oracle.pgql.lang.CommonTranslationUtil.getVariable;
import static oracle.pgql.lang.CommonTranslationUtil.translateExp;
import static oracle.pgql.lang.CommonTranslationUtil.parseLong;
import static oracle.pgql.lang.CommonTranslationUtil.parseInt;

public class SpoofaxAstToGraphQuery {

  private static final String GENERATED_VAR_SUBSTR = "<<anonymous>>";

  private static final int POS_COMMON_PATH_EXPRESSIONS = 0;
  private static final int POS_SELECT_OR_MODIFY = 1;
  private static final int POS_GRAPH_NAME = 2;
  private static final int POS_GRAPH_PATTERN = 3;
  private static final int POS_GROUPBY = 4;
  private static final int POS_HAVING = 5;
  private static final int POS_ORDERBY = 6;
  private static final int POS_LIMITOFFSET = 7;

  private static final int POT_GRAPH_NAME_NAME = 0;

  private static final int POS_COMMON_PATH_EXPRESSION_NAME = 0;
  private static final int POS_COMMON_PATH_EXPRESSION_VERTICES = 1;
  private static final int POS_COMMON_PATH_EXPRESSION_CONNECTIONS = 2;
  private static final int POS_COMMON_PATH_EXPRESSION_CONSTRAINTS = 3;
  private static final int POS_COMMON_PATH_EXPRESSION_COST = 4;

  private static final int POS_COST_EXP = 0;

  private static final int POS_PROJECTION_DISTINCT = 0;
  private static final int POS_PROJECTION_ELEMS = 1;

  private static final int POS_MODIFY_MODIFICATIONS = 0;

  private static final int POS_INSERT_CLAUSE_INTO_CLAUSE = 0;
  private static final int POS_INSERT_CLAUSE_INSERTIONS = 1;
  private static final int POS_INSERT_CLAUSE_INTO_CLAUSE_GRAPH_NAME = 0;
  private static final int POS_VERTEX_INSERTION_NAME = 0;
  private static final int POS_VERTEX_INSERTION_ORIGIN_OFFSET = 1;
  private static final int POS_VERTEX_INSERTION_LABELS = 2;
  private static final int POS_VERTEX_INSERTION_PROPERTIES = 3;
  private static final int POS_EDGE_INSERTION_NAME = 0;
  private static final int POS_EDGE_INSERTION_ORIGIN_OFFSET = 1;
  private static final int POS_EDGE_INSERTION_SRC = 2;
  private static final int POS_EDGE_INSERTION_DST = 3;
  private static final int POS_EDGE_INSERTION_LABELS = 4;
  private static final int POS_EDGE_INSERTION_PROPERTIES = 5;
  private static final int POS_LABELS_LIST = 0;

  private static final int POS_UPDATE_CLAUSE_ELEMENTS = 0;
  private static final int POS_UPDATE_NAME = 0;
  private static final int POS_UPDATE_SET_PROPERTIES = 1;
  private static final int POS_SET_PROPERTIES_EXPRESSIONS = 0;
  private static final int POS_SET_PROPERTY_EXPRESSION_PROPERTY_ACCESS = 0;
  private static final int POS_SET_PROPERTY_EXPRESSION_VALUE_EXPRESSION = 1;

  private static final int POS_DELETION_ELEMENTS = 0;

  private static final int POS_VERTICES = 0;
  private static final int POS_CONNECTIONS = 1;
  private static final int POS_CONSTRAINTS = 2;

  private static final int POS_VERTEX_NAME = 0;
  private static final int POS_VERTEX_ORIGIN_OFFSET = 1;

  private static final int POS_EDGE_SRC = 0;
  private static final int POS_EDGE_NAME = 1;
  private static final int POS_EDGE_DST = 2;
  private static final int POS_EDGE_DIRECTION = 3;
  private static final int POS_EDGE_ORIGIN_OFFSET = 4;

  private static final int POS_PATH_SRC = 0;
  private static final int POS_PATH_DST = 1;
  private static final int POS_PATH_EXPRESSION = 2;
  private static final int POS_PATH_QUANTIFIERS = 3;
  private static final int POS_PATH_QUANTIFIERS_MIN_HOPS = 0;
  private static final int POS_PATH_QUANTIFIERS_MAX_HOPS = 1;
  private static final int POS_PATH_NAME = 4;
  private static final int POS_PATH_DIRECTION = 5;
  private static final int POS_PATH_FINDING_GOAL = 6;
  private static final int POS_PATH_K_VALUE = 7;

  private static final int POS_ORDERBY_EXP = 0;
  private static final int POS_ORDERBY_ORDERING = 1;
  private static final int POS_LIMIT = 0;
  private static final int POS_OFFSET = 1;

  private static final int POS_EXPASVAR_EXP = 0;
  private static final int POS_EXPASVAR_VAR = 1;
  private static final int POS_EXPASVAR_ANONYMOUS = 2;
  private static final int POS_EXPASVAR_ORIGIN_OFFSET = 3;

  private static final int IDENTIFIER_NAME = 0;
  private static final int IDENTIFIER_ORIGINNAME = 1;

  public static PgqlStatement translate(IStrategoTerm ast) throws PgqlException {

    String constructorName = ((IStrategoAppl) ast).getConstructor().getName();

    switch (constructorName) {
      case "NormalizedQuery":
        return translate(ast, new TranslationContext(new HashMap<>(), new HashSet<>(), new HashMap<>()));
      case "CreatePropertyGraph":
        return translateCreatePropertyGraph(ast);
      case "DropPropertyGraph":
        return translateDropPropertyGraph(ast);
      case "CreateExternalSchema":
        return translateCreateExternalSchema(ast);
      case "DropExternalSchema":
        return translateDropExternalSchema(ast);
      default:
        return null; // failed to parse query
    }
  }

  /**
   *
   * @param ast
   *          abstract syntax tree returned by Spoofax parser
   * @param vars
   *          map from variable name to variable
   */
  protected static GraphQuery translate(IStrategoTerm ast, TranslationContext ctx) throws PgqlException {

    // path patterns
    IStrategoTerm commonPathExpressionsT = getList(ast.getSubterm(POS_COMMON_PATH_EXPRESSIONS));
    List<CommonPathExpression> commonPathExpressions = getCommonPathExpressions(commonPathExpressionsT, ctx);

    // graph pattern
    IStrategoTerm graphPatternT = ast.getSubterm(POS_GRAPH_PATTERN);

    GraphPattern graphPattern;
    if (isNone(graphPatternT)) {
      graphPattern = null;
    } else {
      graphPatternT = getSome(graphPatternT);

      // vertices
      IStrategoTerm verticesT = getList(graphPatternT.getSubterm(POS_VERTICES));
      Set<QueryVertex> vertices = new HashSet<>(getQueryVertices(verticesT, ctx));
      Map<String, QueryVertex> vertexMap = new HashMap<>();
      vertices.stream().forEach(vertex -> vertexMap.put(vertex.getName(), vertex));

      // connections
      IStrategoTerm connectionsT = getList(graphPatternT.getSubterm(POS_CONNECTIONS));
      LinkedHashSet<VertexPairConnection> connections = getConnections(connectionsT, ctx, vertexMap);

      giveAnonymousVariablesUniqueHiddenName(vertices, ctx);
      giveAnonymousVariablesUniqueHiddenName(connections, ctx);

      // constraints
      IStrategoTerm constraintsT = getList(graphPatternT.getSubterm(POS_CONSTRAINTS));
      LinkedHashSet<QueryExpression> constraints = getQueryExpressions(constraintsT, ctx);

      // graph pattern
      graphPattern = new GraphPattern(vertices, connections, constraints);
    }

    // GROUP BY
    IStrategoTerm groupByT = ast.getSubterm(POS_GROUPBY);
    GroupBy groupBy = getGroupBy(ctx, groupByT);

    // SELECT or UPDATE or MODIFY
    Projection projection = null;
    IStrategoTerm selectOrModifyT = ast.getSubterm(POS_SELECT_OR_MODIFY);
    String selectOrUpdate = ((IStrategoAppl) selectOrModifyT).getConstructor().getName();
    List<Modification> modifications = null;
    switch (selectOrUpdate) {
      case "SelectClause":
        projection = translateSelectClause(ctx, selectOrModifyT);
        break;
      case "ModifyClause":
        IStrategoTerm modificationsT = selectOrModifyT.getSubterm(POS_MODIFY_MODIFICATIONS);
        modifications = translateModifications(ctx, modificationsT);
        break;
      default:
        throw new IllegalStateException(selectOrUpdate);
    }

    // input graph name
    IStrategoTerm fromT = ast.getSubterm(POS_GRAPH_NAME);
    SchemaQualifiedName graphName = null;
    if (isSome(fromT)) {
      IStrategoTerm graphNameT = getSome(fromT).getSubterm(POT_GRAPH_NAME_NAME);
      graphName = getSchemaQualifiedName(graphNameT);
    }

    // HAVING
    QueryExpression having = tryGetExpression(ast.getSubterm(POS_HAVING), ctx);

    // ORDER BY
    IStrategoTerm orderByT = ast.getSubterm(POS_ORDERBY);
    List<OrderByElem> orderByElems = getOrderByElems(ctx, orderByT);
    OrderBy orderBy = new OrderBy(orderByElems);

    // LIMIT OFFSET
    IStrategoTerm limitOffsetT = ast.getSubterm(POS_LIMITOFFSET);
    QueryExpression limit = tryGetExpression(limitOffsetT.getSubterm(POS_LIMIT), ctx);
    QueryExpression offset = tryGetExpression(limitOffsetT.getSubterm(POS_OFFSET), ctx);

    switch (selectOrUpdate) {
      case "SelectClause":
        return new SelectQuery(commonPathExpressions, projection, graphName, graphPattern, groupBy, having, orderBy,
            limit, offset);
      case "ModifyClause":
        return new ModifyQuery(commonPathExpressions, modifications, graphName, graphPattern, groupBy, having, orderBy,
            limit, offset);
      default:
        throw new IllegalStateException(selectOrUpdate);
    }
  }

  private static Projection translateSelectClause(TranslationContext ctx, IStrategoTerm selectOrUpdateT)
      throws PgqlException {
    Projection projection;
    IStrategoTerm distinctT = selectOrUpdateT.getSubterm(POS_PROJECTION_DISTINCT);
    boolean distinct = isSome(distinctT);

    IStrategoTerm projectionElemsT = selectOrUpdateT.getSubterm(POS_PROJECTION_ELEMS);
    List<ExpAsVar> selectElems;
    if (projectionElemsT.getTermType() == IStrategoTerm.APPL
        && ((IStrategoAppl) projectionElemsT).getConstructor().getName().equals("Star")) {
      // GROUP BY in combination with SELECT *. Even though the parser will generate
      // an error for it, the
      // translation to GraphQuery should succeed (error recovery)
      selectElems = new ArrayList<>();
    } else {
      projectionElemsT = projectionElemsT.getSubterm(0);
      selectElems = getExpAsVars(ctx, projectionElemsT);
    }
    projection = new Projection(distinct, selectElems);
    return projection;
  }

  private static List<Modification> translateModifications(TranslationContext ctx, IStrategoTerm modificationsT)
      throws PgqlException {
    List<Modification> result = new ArrayList<>();
    for (IStrategoTerm modificationT : modificationsT) {
      String constructorName = ((IStrategoAppl) modificationT).getConstructor().getName();
      switch (constructorName) {
        case "InsertClause": {

          IStrategoTerm optionalIntoClauseT = modificationT.getSubterm(POS_INSERT_CLAUSE_INTO_CLAUSE);
          SchemaQualifiedName graphName = null;
          if (isSome(optionalIntoClauseT)) {
            IStrategoTerm intoClauseT = getSome(optionalIntoClauseT);
            graphName = getSchemaQualifiedName(intoClauseT.getSubterm(POS_INSERT_CLAUSE_INTO_CLAUSE_GRAPH_NAME));
          }

          IStrategoTerm insertionsT = modificationT.getSubterm(POS_INSERT_CLAUSE_INSERTIONS);

          List<Insertion> insertions = new ArrayList<>();
          for (IStrategoTerm insertionT : insertionsT) {
            Insertion insertion = translateInsertion(ctx, insertionT);
            insertions.add(insertion);
          }

          result.add(new InsertClause(graphName, insertions));

          break;
        }
        case "UpdateClause": {
          IStrategoTerm updatesT = modificationT.getSubterm(POS_UPDATE_CLAUSE_ELEMENTS);

          List<Update> updates = new ArrayList<>();
          for (IStrategoTerm updateT : updatesT) {
            IStrategoTerm elementT = updateT.getSubterm(POS_UPDATE_NAME);
            VarRef element = (VarRef) translateExp(elementT, ctx);

            IStrategoTerm setPropertiesT = updateT.getSubterm(POS_UPDATE_SET_PROPERTIES);
            List<SetPropertyExpression> setPropertyExpressions = getSetPropertyExpressions(ctx, setPropertiesT);

            updates.add(new Update(element, setPropertyExpressions));
          }

          result.add(new UpdateClause(updates));

          break;
        }
        case "DeleteClause": {
          IStrategoTerm elementsT = modificationT.getSubterm(POS_DELETION_ELEMENTS);
          List<VarRef> deletions = new ArrayList<>();
          for (IStrategoTerm elementT : elementsT) {
            deletions.add((VarRef) translateExp(elementT, ctx));
          }

          result.add(new DeleteClause(deletions));

          break;
        }
        default:
          throw new IllegalArgumentException(constructorName);
      }
    }
    return result;
  }

  private static Insertion translateInsertion(TranslationContext ctx, IStrategoTerm insertionT) throws PgqlException {
    String constructorName = ((IStrategoAppl) insertionT).getConstructor().getName();
    switch (constructorName) {
      case "VertexInsertion": {
        String vertexName = getString(insertionT.getSubterm(POS_VERTEX_INSERTION_NAME));
        QueryVertex vertex = new QueryVertex(vertexName, false);
        IStrategoTerm originPosition = insertionT.getSubterm(POS_VERTEX_INSERTION_ORIGIN_OFFSET);
        ctx.addVar(vertex, vertexName, originPosition);

        IStrategoTerm labelsT = insertionT.getSubterm(POS_VERTEX_INSERTION_LABELS);
        List<QueryExpression> labels = getLabels(ctx, labelsT);

        IStrategoTerm propertiesT = insertionT.getSubterm(POS_VERTEX_INSERTION_PROPERTIES);
        List<SetPropertyExpression> properties = getProperties(ctx, propertiesT);

        return new VertexInsertion(vertex, labels, properties);
      }
      case "DirectedEdgeInsertion": {
        String edgeName = getString(insertionT.getSubterm(POS_EDGE_INSERTION_NAME));

        IStrategoTerm srcVarRefT = insertionT.getSubterm(POS_EDGE_INSERTION_SRC);
        QueryVertex src = (QueryVertex) getVariable(ctx, srcVarRefT);

        IStrategoTerm dstVarRefT = insertionT.getSubterm(POS_EDGE_INSERTION_DST);

        QueryVertex dst = (QueryVertex) getVariable(ctx, dstVarRefT);

        QueryEdge edge = new QueryEdge(src, dst, edgeName, false, Direction.OUTGOING);
        IStrategoTerm originPosition = insertionT.getSubterm(POS_EDGE_INSERTION_ORIGIN_OFFSET);
        ctx.addVar(edge, edgeName, originPosition);

        IStrategoTerm labelsT = insertionT.getSubterm(POS_EDGE_INSERTION_LABELS);
        List<QueryExpression> labels = getLabels(ctx, labelsT);

        IStrategoTerm propertiesT = insertionT.getSubterm(POS_EDGE_INSERTION_PROPERTIES);
        List<SetPropertyExpression> properties = getProperties(ctx, propertiesT);
        return new EdgeInsertion(edge, labels, properties);
      }

      default:
        throw new IllegalArgumentException(constructorName);
    }
  }

  private static List<QueryExpression> getLabels(TranslationContext ctx, IStrategoTerm labelsT) throws PgqlException {
    List<QueryExpression> result = new ArrayList<>();
    if (isSome(labelsT)) {
      IStrategoTerm labelsListT = getSome(labelsT).getSubterm(POS_LABELS_LIST);
      for (IStrategoTerm labelT : labelsListT) {
        String label = getString(labelT.getSubterm(IDENTIFIER_NAME));
        result.add(new ConstString(label));
      }
    }
    return result;
  }

  private static List<SetPropertyExpression> getProperties(TranslationContext ctx, IStrategoTerm propertiesT)
      throws PgqlException {
    if (isSome(propertiesT)) {
      return getSetPropertyExpressions(ctx, getSome(propertiesT));
    } else {
      return new ArrayList<>();
    }
  }

  private static List<SetPropertyExpression> getSetPropertyExpressions(TranslationContext ctx,
      IStrategoTerm setPropertiesT)
      throws PgqlException {
    List<SetPropertyExpression> result = new ArrayList<>();

    IStrategoTerm expressionsT = setPropertiesT.getSubterm(POS_SET_PROPERTIES_EXPRESSIONS);
    for (IStrategoTerm expressionT : expressionsT) {
      IStrategoTerm propertyAccessT = expressionT.getSubterm(POS_SET_PROPERTY_EXPRESSION_PROPERTY_ACCESS);
      QueryExpression propertyAccess = translateExp(propertyAccessT, ctx);
      IStrategoTerm valueExpressionT = expressionT.getSubterm(POS_SET_PROPERTY_EXPRESSION_VALUE_EXPRESSION);
      QueryExpression valueExpression = translateExp(valueExpressionT, ctx);
      if (propertyAccess.getExpType() == ExpressionType.PROP_ACCESS) {
        // error recovery: if it is a reference to a group by key it is not a property access
        result.add(new SetPropertyExpression((PropertyAccess) propertyAccess, valueExpression));
      }
    }

    return result;
  }

  private static QueryExpression tryGetExpression(IStrategoTerm term, TranslationContext ctx) throws PgqlException {
    if (isNone(term)) {
      return null;
    }
    IStrategoAppl expT = (IStrategoAppl) term.getSubterm(0).getSubterm(0);
    return translateExp(expT, ctx);
  }

  private static List<CommonPathExpression> getCommonPathExpressions(IStrategoTerm pathPatternsT,
      TranslationContext ctx)
      throws PgqlException {
    List<CommonPathExpression> result = new ArrayList<>();
    for (IStrategoTerm pathPatternT : pathPatternsT) {
      CommonPathExpression commonPathExpression = getPathExpression(pathPatternT, ctx);
      ctx.getCommonPathExpressions().put(commonPathExpression.getName(), commonPathExpression);
      result.add(commonPathExpression);
    }
    return result;
  }

  private static CommonPathExpression getPathExpression(IStrategoTerm pathPatternT, TranslationContext ctx)
      throws PgqlException {
    String name = getString(pathPatternT.getSubterm(POS_COMMON_PATH_EXPRESSION_NAME));
    // vertices
    IStrategoTerm verticesT = getList(pathPatternT.getSubterm(POS_COMMON_PATH_EXPRESSION_VERTICES));
    List<QueryVertex> vertices = getQueryVertices(verticesT, ctx);
    Map<String, QueryVertex> vertexMap = new HashMap<>();
    vertices.stream().forEach(vertex -> vertexMap.put(vertex.getName(), vertex));

    // connections
    IStrategoTerm connectionsT = getList(pathPatternT.getSubterm(POS_COMMON_PATH_EXPRESSION_CONNECTIONS));
    List<VertexPairConnection> connections = new ArrayList<>();
    for (IStrategoTerm connectionT : connectionsT) {
      if (((IStrategoAppl) connectionT).getConstructor().getName().equals("Edge")) {
        connections.add(getQueryEdge(connectionT, ctx, vertexMap));
      } else {
        connections.add(getPath(connectionT, ctx, vertexMap));
      }
    }

    // constraints
    IStrategoTerm constraintsT = getList(pathPatternT.getSubterm(POS_COMMON_PATH_EXPRESSION_CONSTRAINTS));
    LinkedHashSet<QueryExpression> constraints = getQueryExpressions(constraintsT, ctx);

    // COST clause
    IStrategoTerm costT = pathPatternT.getSubterm(POS_COMMON_PATH_EXPRESSION_COST);
    QueryExpression cost;
    if (isNone(costT)) {
      cost = null;
    } else {
      cost = translateExp(getSome(costT).getSubterm(POS_COST_EXP), ctx);
    }

    return new CommonPathExpression(name, vertices, connections, constraints, cost);
  }

  private static List<QueryVertex> getQueryVertices(IStrategoTerm verticesT, TranslationContext ctx) {
    List<QueryVertex> vertices = new ArrayList<>(verticesT.getSubtermCount());
    for (IStrategoTerm vertexT : verticesT) {
      String vertexName = getString(vertexT.getSubterm(POS_VERTEX_NAME));
      IStrategoTerm originPosition = vertexT.getSubterm(POS_VERTEX_ORIGIN_OFFSET);
      boolean anonymous = vertexName.contains(GENERATED_VAR_SUBSTR);

      QueryVertex vertex = new QueryVertex(vertexName, anonymous);
      ctx.addVar(vertex, vertexName, originPosition);
      vertices.add(vertex);
    }
    return vertices;
  }

  private static LinkedHashSet<QueryExpression> getQueryExpressions(IStrategoTerm constraintsT, TranslationContext ctx)
      throws PgqlException {
    LinkedHashSet<QueryExpression> constraints = new LinkedHashSet<>(constraintsT.getSubtermCount());
    for (IStrategoTerm constraintT : constraintsT) {
      QueryExpression exp = translateExp(constraintT, ctx);
      addQueryExpressions(exp, constraints);
    }
    return constraints;
  }

  private static void addQueryExpressions(QueryExpression exp, Set<QueryExpression> constraints) {
    if (exp.getExpType() == ExpressionType.AND) {
      And and = (And) exp;
      addQueryExpressions(and.getExp1(), constraints);
      addQueryExpressions(and.getExp2(), constraints);
    } else {
      constraints.add(exp);
    }
  }

  private static LinkedHashSet<VertexPairConnection> getConnections(IStrategoTerm connectionsT, TranslationContext ctx,
      Map<String, QueryVertex> vertexMap)
      throws PgqlException {

    LinkedHashSet<VertexPairConnection> result = new LinkedHashSet<>();

    for (IStrategoTerm connectionT : connectionsT) {
      String consName = ((IStrategoAppl) connectionT).getConstructor().getName();

      switch (consName) {
        case "Edge":
          result.add(getQueryEdge(connectionT, ctx, vertexMap));
          break;
        case "Path":
          result.add(getPath(connectionT, ctx, vertexMap));
          break;
        case "ComplexRegularExpressionNotSupported":
        case "ComplexParenthesizedRegularExpressionNotSupported":
          // ignore it
          break;
        default:
          throw new IllegalArgumentException(consName);
      }
    }

    return result;
  }

  private static QueryEdge getQueryEdge(IStrategoTerm edgeT, TranslationContext ctx,
      Map<String, QueryVertex> vertexMap) {
    String name = getString(edgeT.getSubterm(POS_EDGE_NAME));
    String srcName = getString(edgeT.getSubterm(POS_EDGE_SRC));
    String dstName = getString(edgeT.getSubterm(POS_EDGE_DST));
    Direction direction = getDirection(edgeT.getSubterm(POS_EDGE_DIRECTION));
    IStrategoTerm originPosition = edgeT.getSubterm(POS_EDGE_ORIGIN_OFFSET);

    QueryVertex src = getQueryVertex(vertexMap, srcName);
    QueryVertex dst = getQueryVertex(vertexMap, dstName);

    QueryEdge edge = name.contains(GENERATED_VAR_SUBSTR) ? new QueryEdge(src, dst, name, true, direction)
        : new QueryEdge(src, dst, name, false, direction);

    ctx.addVar(edge, name, originPosition);
    return edge;
  }

  private static Direction getDirection(IStrategoTerm directionT) {
    String constructorName = getConstructorName(directionT);
    switch (constructorName) {
      case "Outgoing":
        return Direction.OUTGOING;
      case "Incoming":
        return Direction.INCOMING;
      case "Undirected":
        return Direction.ANY;
      default:
        throw new IllegalArgumentException(constructorName);
    }
  }

  private static QueryVertex getQueryVertex(Map<String, QueryVertex> vertexMap, String vertexName) {
    QueryVariable queryVariable = vertexMap.get(vertexName);
    if (queryVariable.getVariableType() == VariableType.VERTEX) {
      return (QueryVertex) queryVariable;
    } else {
      // query has syntactic error and although Spoofax generates a grammatically
      // correct AST, the AST is not
      // semantically correct
      QueryVertex dummyVertex = new QueryVertex(vertexName, false);
      return dummyVertex;
    }
  }

  private static QueryPath getPath(IStrategoTerm pathT, TranslationContext ctx, Map<String, QueryVertex> vertexMap)
      throws PgqlException {

    String pathFindingGoal = ((IStrategoAppl) pathT.getSubterm(POS_PATH_FINDING_GOAL)).getConstructor().getName();
    switch (pathFindingGoal) {
      case "Reaches":
        return getReaches(pathT, ctx, vertexMap);
      case "Shortest":
        return getShortestCheapest(pathT, ctx, vertexMap, PathFindingGoal.SHORTEST);
      case "Cheapest":
        return getShortestCheapest(pathT, ctx, vertexMap, PathFindingGoal.CHEAPEST);
      default:
        throw new UnsupportedOperationException(pathFindingGoal);
    }
  }

  private static QueryPath getReaches(IStrategoTerm pathT, TranslationContext ctx, Map<String, QueryVertex> vertexMap)
      throws PgqlException {
    String label = getString(pathT.getSubterm(POS_PATH_EXPRESSION));
    long minHops = getMinHops(pathT);
    long maxHops = getMaxHops(pathT);

    CommonPathExpression commonPathExpression = ctx.getCommonPathExpressions().get(label);

    if (commonPathExpression == null) { // no path expression defined for the label; generate one here
      QueryVertex src = new QueryVertex("n", true);
      QueryVertex dst = new QueryVertex("m", true);
      VertexPairConnection edge = new QueryEdge(src, dst, "e", true, Direction.OUTGOING);

      List<QueryExpression> args = new ArrayList<>();
      args.add(new VarRef(edge));
      args.add(new ConstString(label));
      QueryExpression labelExp = new QueryExpression.FunctionCall("has_label", args);

      List<QueryVertex> vertices = new ArrayList<>();
      vertices.add(src);
      vertices.add(dst);
      List<VertexPairConnection> connections = new ArrayList<>();
      connections.add(edge);
      Set<QueryExpression> constraints = new HashSet<>();
      constraints.add(labelExp);

      commonPathExpression = new CommonPathExpression(label, vertices, connections, constraints);
    }

    String srcName = getString(pathT.getSubterm(POS_PATH_SRC));
    String dstName = getString(pathT.getSubterm(POS_PATH_DST));
    String name = getString(pathT.getSubterm(POS_PATH_NAME));
    Direction direction = getDirection(pathT.getSubterm(POS_PATH_DIRECTION));
    QueryVertex src = getQueryVertex(vertexMap, srcName);
    QueryVertex dst = getQueryVertex(vertexMap, dstName);
    PathFindingGoal goal = PathFindingGoal.REACHES;
    int kValue = -1;

    QueryPath path = name.contains(GENERATED_VAR_SUBSTR)
        ? new QueryPath(src, dst, name, commonPathExpression, true, minHops, maxHops, goal, kValue, direction)
        : new QueryPath(src, dst, name, commonPathExpression, false, minHops, maxHops, goal, kValue, direction);

    return path;
  }

  private static long getMinHops(IStrategoTerm pathT) throws PgqlException {
    return getMinMaxHops(pathT, true);
  }

  private static long getMaxHops(IStrategoTerm pathT) throws PgqlException {
    return getMinMaxHops(pathT, false);
  }

  private static long getMinMaxHops(IStrategoTerm pathT, boolean min) throws PgqlException {
    IStrategoTerm pathQuantifiersT = pathT.getSubterm(POS_PATH_QUANTIFIERS);
    if (isSome(pathQuantifiersT)) {
      pathQuantifiersT = getSome(pathQuantifiersT);
      int position = min ? POS_PATH_QUANTIFIERS_MIN_HOPS : POS_PATH_QUANTIFIERS_MAX_HOPS;
      return parseLong(pathQuantifiersT.getSubterm(position));
    } else {
      return 1;
    }
  }

  private static QueryPath getShortestCheapest(IStrategoTerm pathT, TranslationContext ctx,
      Map<String, QueryVertex> vertexMap, PathFindingGoal goal)
      throws PgqlException {
    String srcName = getString(pathT.getSubterm(POS_PATH_SRC));
    String dstName = getString(pathT.getSubterm(POS_PATH_DST));
    long minHops = getMinHops(pathT);
    long maxHops = getMaxHops(pathT);
    String name = getString(pathT.getSubterm(POS_PATH_NAME));
    Direction direction = getDirection(pathT.getSubterm(POS_PATH_DIRECTION));
    QueryVertex src = getQueryVertex(vertexMap, srcName);
    QueryVertex dst = getQueryVertex(vertexMap, dstName);

    IStrategoTerm pathExpressionT = pathT.getSubterm(POS_PATH_EXPRESSION);
    CommonPathExpression pathExpression = getPathExpression(pathExpressionT, ctx);

    IStrategoTerm kValueT = pathT.getSubterm(POS_PATH_K_VALUE);

    int kValue = kValueT.getTermType() == IStrategoTerm.APPL && isNone(kValueT) ? -1
        : parseInt(pathT.getSubterm(POS_PATH_K_VALUE));

    QueryPath path = new QueryPath(src, dst, name, pathExpression, true, minHops, maxHops, goal, kValue, direction);

    return path;
  }

  private static void giveAnonymousVariablesUniqueHiddenName(Collection<? extends QueryVariable> variables,
      TranslationContext ctx) {

    for (QueryVariable var : variables) {
      if (var.isAnonymous()) {
        String name = var.getName().replace(GENERATED_VAR_SUBSTR, "anonymous");
        while (ctx.isVariableNameInUse(name)) {
          name += "_2";
        }
        var.setName(name);
      }

      // recurse for regular path expressions
      if (var.getVariableType() == VariableType.PATH) {
        QueryPath path = (QueryPath) var;
        giveAnonymousVariablesUniqueHiddenName(path.getConnections(), ctx);
        giveAnonymousVariablesUniqueHiddenName(path.getVertices(), ctx);
      }
    }
  }

  private static GroupBy getGroupBy(TranslationContext ctx, IStrategoTerm groupByT) throws PgqlException {
    String consName = ((IStrategoAppl) groupByT).getConstructor().getName();
    switch (consName) {
      case "Some": // explicit GROUP BY
        IStrategoTerm groupByElemsT = getList(groupByT);
        return new GroupBy(getExpAsVars(ctx, groupByElemsT));
      case "CreateOneGroup": // implicit GROUP BY (e.g. SELECT has aggregation)
        return new GroupBy(Collections.emptyList());
      case "None": // no GROUP BY
        return null;
      default:
        throw new IllegalArgumentException(consName);
    }
  }

  private static List<ExpAsVar> getExpAsVars(TranslationContext ctx, IStrategoTerm expAsVarsT) throws PgqlException {
    List<ExpAsVar> expAsVars = new ArrayList<>(expAsVarsT.getSubtermCount());
    for (IStrategoTerm expAsVarT : expAsVarsT) {
      QueryExpression exp = translateExp(expAsVarT.getSubterm(POS_EXPASVAR_EXP), ctx);
      IStrategoTerm columnName = expAsVarT.getSubterm(POS_EXPASVAR_VAR);
      String varName = getString(columnName.getSubterm(IDENTIFIER_NAME));
      IStrategoTerm originNameT = columnName.getSubterm(IDENTIFIER_ORIGINNAME);
      String originName = isNone(originNameT) ? null : getString(originNameT);
      boolean anonymous = ((IStrategoAppl) expAsVarT.getSubterm(POS_EXPASVAR_ANONYMOUS)).getConstructor().getName()
          .equals("Anonymous");
      IStrategoTerm originPosition = expAsVarT.getSubterm(POS_EXPASVAR_ORIGIN_OFFSET);

      ExpAsVar expAsVar = new ExpAsVar(exp, varName, anonymous, originName);
      expAsVars.add(expAsVar);
      ctx.addVar(expAsVar, varName, originPosition);
    }
    return expAsVars;
  }

  private static List<OrderByElem> getOrderByElems(TranslationContext ctx, IStrategoTerm orderByT)
      throws PgqlException {
    List<OrderByElem> orderByElems = new ArrayList<>();
    if (!isNone(orderByT)) { // has ORDER BY
      IStrategoTerm orderByElemsT = getList(orderByT);
      for (IStrategoTerm orderByElemT : orderByElemsT) {
        QueryExpression exp = translateExp(orderByElemT.getSubterm(POS_ORDERBY_EXP), ctx);
        boolean ascending = ((IStrategoAppl) orderByElemT.getSubterm(POS_ORDERBY_ORDERING)).getConstructor().getName()
            .equals("Asc");
        orderByElems.add(new OrderByElem(exp, ascending));
      }
    }
    return orderByElems;
  }
}
