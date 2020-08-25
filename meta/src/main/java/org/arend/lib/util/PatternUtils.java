package org.arend.lib.util;

import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.ConcretePattern;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.core.body.CorePattern;
import org.arend.ext.core.context.CoreBinding;
import org.arend.ext.core.context.CoreParameter;
import org.arend.ext.core.definition.CoreConstructor;
import org.arend.ext.core.definition.CoreDefinition;
import org.arend.ext.core.definition.CoreFunctionDefinition;
import org.arend.ext.core.expr.CoreClassCallExpression;
import org.arend.ext.core.expr.CoreDataCallExpression;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.core.expr.CoreSigmaExpression;
import org.arend.ext.core.ops.NormalizationMode;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.variable.VariableRenamerFactory;

import java.util.*;

public class PatternUtils {
  public static ConcretePattern toConcrete(CorePattern pattern, VariableRenamerFactory renamer, ConcreteFactory factory, Map<CoreBinding, ArendRef> bindings) {
    if (pattern.isAbsurd()) {
      return factory.tuplePattern();
    }

    if (pattern.getBinding() != null) {
      return factory.refPattern(makeRef(pattern.getBinding(), renamer, factory, bindings), null);
    }

    List<ConcretePattern> subpatterns = toConcrete(pattern.getSubPatterns(), renamer, factory, bindings);
    CoreDefinition def = pattern.getConstructor();
    return def == null ? factory.tuplePattern(subpatterns) : factory.conPattern(def.getRef(), subpatterns);
  }

  public static List<ConcretePattern> toConcrete(Collection<? extends CorePattern> patterns, VariableRenamerFactory renamer, ConcreteFactory factory, Map<CoreBinding, ArendRef> bindings) {
    List<ConcretePattern> result = new ArrayList<>(patterns.size());
    for (CorePattern pattern : patterns) {
      result.add(toConcrete(pattern, renamer, factory, bindings));
    }
    return result;
  }

  private static ArendRef makeRef(CoreBinding binding, VariableRenamerFactory renamer, ConcreteFactory factory, Map<CoreBinding, ArendRef> bindings) {
    ArendRef ref = bindings == null ? null : bindings.get(binding);
    if (ref == null) {
      ref = factory.local(renamer.getNameFromBinding(binding, null));
      if (bindings != null) {
        bindings.put(binding, ref);
      }
    }
    return ref;
  }

  public static ConcreteExpression toExpression(CorePattern pattern, VariableRenamerFactory renamer, ConcreteFactory factory, Map<CoreBinding, ArendRef> bindings) {
    if (pattern.getBinding() != null) {
      return factory.ref(makeRef(pattern.getBinding(), renamer, factory, bindings));
    }

    if (pattern.getConstructor() == null) {
      // TODO: Invoke 'constructor' meta
      return factory.tuple(toExpression(pattern.getSubPatterns(), renamer, factory, bindings));
    } else {
      return factory.app(factory.ref(pattern.getConstructor().getRef()), true, toExpression(pattern.getSubPatterns(), renamer, factory, bindings));
    }
  }

  public static List<ConcreteExpression> toExpression(Collection<? extends CorePattern> patterns, VariableRenamerFactory renamer, ConcreteFactory factory, Map<CoreBinding, ArendRef> bindings) {
    List<ConcreteExpression> result = new ArrayList<>(patterns.size());
    for (CorePattern pattern : patterns) {
      result.add(toExpression(pattern, renamer, factory, bindings));
    }
    return result;
  }


  public static CoreParameter getAllBindings(Collection<? extends CorePattern> patterns) {
    for (CorePattern pattern : patterns) {
      CoreParameter param = pattern.getAllBindings();
      if (param.hasNext()) {
        return param;
      }
    }
    return null;
  }

  public static boolean isAbsurd(CorePattern pattern) {
    if (pattern.isAbsurd()) {
      return true;
    }
    for (CorePattern subPattern : pattern.getSubPatterns()) {
      if (isAbsurd(subPattern)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isAbsurd(Collection<? extends CorePattern> patterns) {
    for (CorePattern pattern : patterns) {
      if (isAbsurd(pattern)) {
        return true;
      }
    }
    return false;
  }


  public static boolean isTrivial(CorePattern pattern) {
    if (pattern.getBinding() != null) {
      return true;
    }
    if (pattern.isAbsurd() || pattern.getConstructor() != null) {
      return false;
    }
    for (CorePattern subPattern : pattern.getSubPatterns()) {
      if (!isTrivial(subPattern)) {
        return false;
      }
    }
    return true;
  }

  public static boolean isTrivial(Collection<? extends CorePattern> patterns) {
    for (CorePattern pattern : patterns) {
      if (!isTrivial(pattern)) {
        return false;
      }
    }
    return true;
  }

  public static boolean refines(CorePattern pattern1, CorePattern pattern2) {
    return pattern2.getBinding() != null || pattern1.isAbsurd() && pattern2.isAbsurd() || pattern1.getConstructor() == pattern2.getConstructor() && refines(pattern1.getSubPatterns(), pattern2.getSubPatterns()) || pattern2.getBinding() != null && isTrivial(pattern1);
  }

  public static boolean refines(List<? extends CorePattern> patterns1, List<? extends CorePattern> patterns2) {
    if (patterns1.size() != patterns2.size()) {
      return false;
    }

    for (int i = 0; i < patterns1.size(); i++) {
      if (!refines(patterns1.get(i), patterns2.get(i))) {
        return false;
      }
    }

    return true;
  }


  public static boolean unify(CorePattern pattern1, CorePattern pattern2, Map<CoreBinding, CorePattern> subst1, Map<CoreBinding, CorePattern> subst2) {
    if (pattern1.isAbsurd() && pattern2.isAbsurd()) {
      return true;
    }

    if (pattern1.getBinding() != null || pattern2.getBinding() != null) {
      if (subst1 != null && pattern1.getBinding() != null) {
        subst1.put(pattern1.getBinding(), pattern2);
      }
      if (subst2 != null && pattern2.getBinding() != null) {
        subst2.put(pattern2.getBinding(), pattern1);
      }
      return true;
    }

    if (pattern1.getConstructor() != pattern2.getConstructor() || pattern1.isAbsurd() || pattern2.isAbsurd()) {
      return false;
    }

    return unify(pattern1.getSubPatterns(), pattern2.getSubPatterns(), subst1, subst2);
  }

  public static boolean unify(List<? extends CorePattern> patterns1, List<? extends CorePattern> patterns2, Map<CoreBinding, CorePattern> subst1, Map<CoreBinding, CorePattern> subst2) {
    if (patterns1.size() != patterns2.size()) {
      return false;
    }

    for (int i = 0; i < patterns1.size(); i++) {
      if (!unify(patterns1.get(i), patterns2.get(i), subst1, subst2)) {
        return false;
      }
    }

    return true;
  }


  /**
   * Checks coverage for a list of patterns with {@code type} as their type.
   */
  private static boolean checkCoverage(List<? extends CorePattern> patterns, CoreExpression type) {
    for (CorePattern pattern : patterns) {
      if (pattern.isAbsurd() || pattern.getBinding() != null || pattern.getConstructor() instanceof CoreFunctionDefinition) {
        return true;
      }
    }

    type = type.normalize(NormalizationMode.WHNF);

    if (patterns.isEmpty()) {
      if (!(type instanceof CoreDataCallExpression)) {
        return false;
      }
      List<CoreConstructor> constructors = ((CoreDataCallExpression) type).computeMatchedConstructors();
      return constructors != null && constructors.isEmpty();
    }

    boolean isTuple = patterns.get(0).getConstructor() == null;
    for (CorePattern pattern : patterns) {
      if (isTuple != (pattern.getConstructor() == null)) {
        return false;
      }
    }

    if (isTuple) {
      CoreParameter parameters;
      if (type instanceof CoreSigmaExpression) {
        parameters = ((CoreSigmaExpression) type).getParameters();
      } else if (type instanceof CoreClassCallExpression) {
        parameters = ((CoreClassCallExpression) type).getClassFieldParameters();
      } else {
        return false;
      }
      return checkCoverage(patterns, parameters);
    }

    if (!(type instanceof CoreDataCallExpression)) {
      return false;
    }

    List<CoreDataCallExpression.ConstructorWithDataArguments> constructors = ((CoreDataCallExpression) type).computeMatchedConstructorsWithDataArguments();
    if (constructors == null) {
      return false;
    }

    Map<CoreDefinition, List<CorePattern>> map = new HashMap<>();
    for (CorePattern pattern : patterns) {
      map.computeIfAbsent(pattern.getConstructor(), k -> new ArrayList<>()).add(pattern);
    }

    for (CoreDataCallExpression.ConstructorWithDataArguments conCall : constructors) {
      List<CorePattern> list = map.get(conCall.getConstructor());
      if (list == null || !checkCoverage(list, conCall.getParameters())) {
        return false;
      }
    }

    return true;
  }

  private static boolean checkCoverage(List<? extends CorePattern> patterns, CoreParameter parameters) {
    List<List<? extends CorePattern>> rows = new ArrayList<>(patterns.size());
    for (CorePattern pattern : patterns) {
      rows.add(pattern.getSubPatterns());
    }

    int numberOfColumns = rows.get(0).size();
    for (List<? extends CorePattern> row : rows) {
      if (row.size() != numberOfColumns) {
        return false;
      }
    }

    CoreParameter param = parameters;
    for (int i = 0; i < numberOfColumns; i++) {
      if (!param.hasNext()) {
        return false;
      }
      List<CorePattern> column = new ArrayList<>(rows.size());
      for (List<? extends CorePattern> row : rows) {
        column.add(row.get(i));
      }
      if (!checkCoverage(column, param.getTypeExpr())) {
        return false;
      }
      param = param.getNext();
    }

    return !param.hasNext();
  }

  /**
   * @return indices of rows from {@code actualRows} that cover {@code row}, or false if {@code actualRows} do not cover {@code row}
   */
  public static List<Integer> computeCovering(List<? extends List<? extends CorePattern>> actualRows, List<? extends CorePattern> row) {
    for (CorePattern pattern : row) {
      if (pattern.isAbsurd()) {
        return Collections.emptyList();
      }
    }
    if (actualRows.isEmpty()) {
      return null;
    }

    List<Integer> coveringIndices = new ArrayList<>();
    Map<CoreBinding, List<CorePattern>> substs = new HashMap<>();
    for (int i = 0; i < actualRows.size(); i++) {
      Map<CoreBinding, CorePattern> subst = new HashMap<>();
      if (unify(actualRows.get(i), row, null, subst)) {
        coveringIndices.add(i);
        for (Map.Entry<CoreBinding, CorePattern> entry : subst.entrySet()) {
          substs.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(entry.getValue());
        }
      }
    }

    if (coveringIndices.isEmpty()) {
      return null;
    }

    for (Map.Entry<CoreBinding, List<CorePattern>> entry : substs.entrySet()) {
      if (!checkCoverage(entry.getValue(), entry.getKey().getTypeExpr())) {
        return null;
      }
    }

    return coveringIndices;
  }

  public static List<CorePattern> subst(Collection<? extends CorePattern> patterns, Map<CoreBinding, CorePattern> map) {
    List<CorePattern> result = new ArrayList<>(patterns.size());
    for (CorePattern pattern : patterns) {
      result.add(pattern.subst(map));
    }
    return result;
  }
}
