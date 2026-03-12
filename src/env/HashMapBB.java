package vocab;

import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import jason.JasonException;
import jason.asSemantics.Agent;
import jason.asSemantics.Unifier;
import jason.asSyntax.ASSyntax;
import jason.asSyntax.Atom;
import jason.asSyntax.Literal;
import jason.asSyntax.PredicateIndicator;
import jason.asSyntax.Term;
import jason.asSyntax.parser.ParseException;
import jason.bb.BeliefBase;

/**
 * A HashMap-backed belief base that provides O(1) lookup for beliefs
 * registered with a primary key pattern (e.g. {@code word(key,_)}).
 *
 * <p>Indexed beliefs are stored <b>only</b> in a HashMap keyed by the
 * first argument, bypassing the overhead of DefaultBeliefBase entirely.
 * Non-indexed beliefs are stored in a lightweight namespace-aware map.</p>
 *
 * <h3>Configuration (programmatic):</h3>
 * <pre>
 * agChild.bbClass = new ClassParameters("vocab.HashMapBB");
 * agChild.bbClass.addParameter("\"word(key,_)\"");
 * </pre>
 */
public class HashMapBB extends BeliefBase {

    // ===================== Indexed beliefs =====================

    /** Registered index patterns: functor → expected arity. */
    private final Map<String, Integer> indexedPatterns = new HashMap<>();

    /**
     * Key argument position for each indexed functor.
     * -1 means "single-entry" (no key arg — only one belief of this functor/arity).
     */
    private final Map<String, Integer> indexedKeyArg = new HashMap<>();

    /** Index storage: functor → (key-string → Literal). */
    private final Map<String, Map<String, Literal>> indexed = new HashMap<>();
    private int indexedCount = 0;

    // ===================== Non-indexed beliefs =====================

    /** Non-indexed storage: namespace → PredicateIndicator → ordered list. */
    private final Map<Atom, Map<PredicateIndicator, LinkedList<Literal>>> nameSpaces = new HashMap<>();
    private int otherCount = 0;

    // ===================== Percept tracking =====================

    private final Set<Literal> percepts = new HashSet<>();

    // ===================== Namespace properties =====================

    private final Map<Atom, Map<Atom, Term>> nsProps = new HashMap<>();

    // ===================== Constructor =====================

    public HashMapBB() {
        nameSpaces.put(Literal.DefaultNS, new HashMap<>());
    }

    // ===================== Initialisation =====================

    @Override
    public void init(Agent ag, String[] args) {
        for (String arg : args) {
            try {
                Literal pattern = ASSyntax.parseLiteral(arg);
                String functor = pattern.getFunctor();
                int arity = pattern.getArity();
                indexedPatterns.put(functor, arity);
                indexed.put(functor, new HashMap<>());
                // Find which argument (if any) is marked as the key.
                // e.g. word(key,_) → keyArg=0; words_to_speak(_) → keyArg=-1
                int keyArg = -1;
                for (int i = 0; i < arity; i++) {
                    if (pattern.getTerm(i).toString().equals("key")) {
                        keyArg = i;
                        break;
                    }
                }
                indexedKeyArg.put(functor, keyArg);
            } catch (ParseException e) {
                System.err.println("HashMapBB: failed to parse index pattern: " + arg);
            }
        }
    }

    // ===================== Helpers =====================

    private boolean isIndexed(Literal l) {
        Integer arity = indexedPatterns.get(l.getFunctor());
        return arity!=null && l.getArity() == arity;
    }

    /** Sentinel key used for single-entry indexed beliefs (no key arg). */
    private static final String SINGLETON_KEY = "__singleton__";

    private String keyOf(Literal l) {
        int keyArg = indexedKeyArg.get(l.getFunctor());
        if (keyArg < 0) return SINGLETON_KEY;
        return l.getTerm(keyArg).toString();
    }


    // ===================== Direct indexed access =====================

    /** Direct O(1) get for indexed beliefs, bypassing pattern creation and isIndexed checks. */
    public Literal getIndexedDirect(String functor, String key) {
        Map<String, Literal> inner = indexed.get(functor);
        return (inner != null) ? inner.get(key) : null;
    }

    /** Direct O(1) put for indexed beliefs. Returns previous value (or null). */
    public Literal putIndexedDirect(String functor, String key, Literal l) {
        Map<String, Literal> inner = indexed.get(functor);
        if (inner == null) return null;
        Literal old = inner.put(key, l);
        if (old == null) indexedCount++;
        return old;
    }

    /** Get or create the namespace map for a given namespace. */
    private Map<PredicateIndicator, LinkedList<Literal>> provideNS(Atom ns) {
        return nameSpaces.computeIfAbsent(ns, k -> new HashMap<>());
    }

    // ===================== ADD =====================

    @Override
    public boolean add(Literal l) throws JasonException {
        if (isIndexed(l)) {
            String key = keyOf(l);
            Map<String, Literal> inner = indexed.get(l.getFunctor());
            if (inner.put(key, l) == null) {
                indexedCount++;
            }
            return true;
        }
        return addNonIndexed(l, false);
    }

    @Override
    public boolean add(int index, Literal l) throws JasonException {
        if (isIndexed(l)) {
            return add(l);
        }
        return addNonIndexed(l, index != 0);
    }

    private boolean addNonIndexed(Literal l, boolean addAtEnd) throws JasonException {
        // Annotation merging: if a structurally identical belief exists,
        // merge the new annotations into it.
        Literal existing = containsNonIndexed(l);
        if (existing != null && !existing.isRule()) {
            if (existing.importAnnots(l)) {
                if (l.hasAnnot(TPercept)) {
                    percepts.add(existing);
                }
                return true;
            }
            return false;
        }

        // New belief — clone to decouple from caller's reference
        l = l.copy();
        Map<PredicateIndicator, LinkedList<Literal>> nsMap = provideNS(l.getNS());
        LinkedList<Literal> list = nsMap.computeIfAbsent(
                l.getPredicateIndicator(), k -> new LinkedList<>());
        if (addAtEnd) {
            list.addLast(l);
        } else {
            list.addFirst(l);
        }
        if (l.hasAnnot(TPercept)) {
            percepts.add(l);
        }
        otherCount++;
        return true;
    }

    // ===================== REMOVE =====================

    @Override
    public boolean remove(Literal l) {
        if (isIndexed(l)) {
            Map<String, Literal> inner = indexed.get(l.getFunctor());
            int keyArg = indexedKeyArg.get(l.getFunctor());

            if (keyArg < 0) {
                // Single-entry: remove the sole entry
                if (!inner.isEmpty()) {
                    inner.clear();
                    indexedCount--;
                    return true;
                }
                return false;
            }

            Term arg = l.getTerm(keyArg);
            if (arg.isGround()) {
                // O(1) direct lookup
                if (inner.remove(arg.toString()) != null) {
                    indexedCount--;
                    return true;
                }
                return false;
            }
            // Unground key (e.g. variable '_') — remove first match
            Iterator<Map.Entry<String, Literal>> it = inner.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Literal> entry = it.next();
                if (new Unifier().unifies(l, entry.getValue())) {
                    it.remove();
                    indexedCount--;
                    return true;
                }
            }
            return false;
        }
        return removeNonIndexed(l);
    }

    private boolean removeNonIndexed(Literal l) {
        Literal bl = containsNonIndexed(l);
        if (bl != null && l.hasSubsetAnnot(bl)) {
            if (l.hasAnnot(TPercept)) {
                percepts.remove(bl);
            }
            boolean annotDeleted = bl.delAnnots(l.getAnnots());
            if (!bl.hasSource()) {
                // No source annotations remain — remove from BB entirely
                removeEntry(bl);
                return true;
            }
            return annotDeleted;
        }
        return false;
    }

    private void removeEntry(Literal l) {
        Map<PredicateIndicator, LinkedList<Literal>> nsMap = nameSpaces.get(l.getNS());
        if (nsMap == null) return;
        LinkedList<Literal> list = nsMap.get(l.getPredicateIndicator());
        if (list == null) return;
        list.remove(l);
        if (list.isEmpty()) {
            nsMap.remove(l.getPredicateIndicator());
        }
        otherCount--;
    }

    // ===================== CONTAINS =====================

    @Override
    public Literal contains(Literal l) {
        if (isIndexed(l)) {
            Map<String, Literal> inner = indexed.get(l.getFunctor());
            if (inner == null || inner.isEmpty()) return null;
            int keyArg = indexedKeyArg.get(l.getFunctor());
            if (keyArg < 0) {
                // Single-entry: return the sole stored belief if it unifies
                Literal sole = inner.values().iterator().next();
                return new Unifier().unifies(l, sole) ? sole : null;
            }
            Term arg = l.getTerm(keyArg);
            if (arg.isGround()) {
                return inner.get(arg.toString());
            }
            // Unground key — linear scan for a unifying match
            Unifier u = new Unifier();
            for (Literal stored : inner.values()) {
                if (u.unifies(l, stored)) {
                    return stored;
                }
                u.clear();
            }
            return null;
        }
        return containsNonIndexed(l);
    }

    /** Finds a structurally matching belief (ignoring annotations). */
    private Literal containsNonIndexed(Literal l) {
        Map<PredicateIndicator, LinkedList<Literal>> nsMap = nameSpaces.get(l.getNS());
        if (nsMap == null) return null;
        LinkedList<Literal> list = nsMap.get(l.getPredicateIndicator());
        if (list == null) return null;
        for (Literal bl : list) {
            if (bl.equalsAsStructure(l)) {
                return bl;
            }
        }
        return null;
    }

    // ===================== CANDIDATE BELIEFS =====================

    @Override
    public Iterator<Literal> getCandidateBeliefs(PredicateIndicator pi) {
        String functor = pi.getFunctor();
        Integer arity = indexedPatterns.get(functor);
        if (arity != null && pi.getArity() == arity) {
            Map<String, Literal> inner = indexed.get(functor);
            if (inner == null || inner.isEmpty()) return null;
            return new ArrayList<>(inner.values()).iterator();
        }
        Map<PredicateIndicator, LinkedList<Literal>> nsMap = nameSpaces.get(pi.getNS());
        if (nsMap == null) return null;
        LinkedList<Literal> list = nsMap.get(pi);
        if (list == null || list.isEmpty()) return null;
        return list.iterator();
    }

    @Override
    public Iterator<Literal> getCandidateBeliefs(Literal l, Unifier u) {
        if (l.isVar()) {
            return iterator();
        }

        if (isIndexed(l)) {
            Map<String, Literal> inner = indexed.get(l.getFunctor());
            if (inner == null || inner.isEmpty()) return null;

            int keyArg = indexedKeyArg.get(l.getFunctor());
            if (keyArg < 0) {
                // Single-entry: return the sole belief
                return new ArrayList<>(inner.values()).iterator();
            }

            Term arg = l.getTerm(keyArg);
            Term resolved = arg.capply(u);

            if (resolved.isGround()) {
                // O(1) direct lookup
                Literal found = inner.get(resolved.toString());
                if (found != null) {
                    return Collections.singletonList(found).iterator();
                }
                return null;
            }
            // Unbound variable — return all indexed beliefs for this functor
            return new ArrayList<>(inner.values()).iterator();
        }

        // Non-indexed: resolve namespace if needed
        Atom ns = l.getNS();
        if (ns.isVar()) {
            l = (Literal) l.capply(u);
            ns = l.getNS();
        }
        if (ns.isVar()) {
            return iterator();
        }

        Map<PredicateIndicator, LinkedList<Literal>> nsMap = nameSpaces.get(ns);
        if (nsMap == null) return null;
        LinkedList<Literal> list = nsMap.get(l.getPredicateIndicator());
        if (list == null || list.isEmpty()) return null;
        return list.iterator();
    }

    // ===================== PERCEPTS =====================

    @Override
    public Iterator<Literal> getPercepts() {
        final Iterator<Literal> it = percepts.iterator();
        return new Iterator<Literal>() {
            Literal current = null;

            public boolean hasNext() { return it.hasNext(); }

            public Literal next() {
                current = it.next();
                return current;
            }

            public void remove() {
                // Remove from percepts set
                it.remove();
                // Remove the percept annotation
                current.delAnnot(BeliefBase.TPercept);
                // If no sources remain, remove from BB
                if (!current.hasSource()) {
                    removeEntry(current);
                }
            }
        };
    }

    // ===================== SIZE / CLEAR / ITERATOR =====================

    @Override
    public int size() {
        return indexedCount + otherCount;
    }

    @Override
    public void clear() {
        for (Map<String, Literal> inner : indexed.values()) {
            inner.clear();
        }
        indexedCount = 0;
        nameSpaces.clear();
        nameSpaces.put(Literal.DefaultNS, new HashMap<>());
        otherCount = 0;
        percepts.clear();
    }

    @Override
    public Iterator<Literal> iterator() {
        System.out.println("HashMapBB.iterator() called.");
        // Collect all beliefs into a snapshot list
        List<Literal> all = new ArrayList<>(indexedCount + otherCount);
        for (Map<String, Literal> inner : indexed.values()) {
            all.addAll(inner.values());
        }
        for (Map<PredicateIndicator, LinkedList<Literal>> nsMap : nameSpaces.values()) {
            for (LinkedList<Literal> list : nsMap.values()) {
                all.addAll(list);
            }
        }
        return all.iterator();
    }

    // ===================== ABOLISH =====================

    @Override
    public boolean abolish(Atom namespace, PredicateIndicator pi) {
        String functor = pi.getFunctor();
        Integer arity = indexedPatterns.get(functor);
        if (arity != null && pi.getArity() == arity) {
            Map<String, Literal> inner = indexed.get(functor);
            if (inner != null && !inner.isEmpty()) {
                indexedCount -= inner.size();
                inner.clear();
                return true;
            }
            return false;
        }
        Map<PredicateIndicator, LinkedList<Literal>> nsMap = nameSpaces.get(namespace);
        if (nsMap == null) return false;
        LinkedList<Literal> removed = nsMap.remove(pi);
        if (removed != null) {
            otherCount -= removed.size();
            // Remove any percepts
            Iterator<Literal> pit = percepts.iterator();
            while (pit.hasNext()) {
                if (pit.next().getPredicateIndicator().equals(pi)) {
                    pit.remove();
                }
            }
            return true;
        }
        return false;
    }

    // ===================== NAMESPACE MANAGEMENT =====================

    @Override
    public Set<Atom> getNameSpaces() {
        Set<Atom> all = new HashSet<>(nameSpaces.keySet());
        // Indexed beliefs don't carry namespace info in our index,
        // but the beliefs themselves do. Scan to find any extra namespaces.
        for (Map<String, Literal> inner : indexed.values()) {
            for (Literal l : inner.values()) {
                all.add(l.getNS());
            }
        }
        return all;
    }

    @Override
    public void setNameSpaceProp(Atom ns, Atom key, Term value) {
        nsProps.computeIfAbsent(ns, k -> new HashMap<>()).put(key, value);
    }

    @Override
    public Term getNameSpaceProp(Atom ns, Atom key) {
        Map<Atom, Term> props = nsProps.get(ns);
        return props != null ? props.get(key) : null;
    }

    @Override
    public Set<Atom> getNameSpaceProps(Atom ns) {
        Map<Atom, Term> props = nsProps.get(ns);
        return props != null ? props.keySet() : new HashSet<>();
    }

    // ===================== CLONE =====================

    @Override
    public BeliefBase clone() {
        HashMapBB cl = new HashMapBB();
        cl.indexedPatterns.putAll(this.indexedPatterns);
        cl.indexedKeyArg.putAll(this.indexedKeyArg);
        for (Map.Entry<String, Map<String, Literal>> entry : this.indexed.entrySet()) {
            Map<String, Literal> clonedInner = new HashMap<>();
            for (Map.Entry<String, Literal> e : entry.getValue().entrySet()) {
                clonedInner.put(e.getKey(), e.getValue().copy());
            }
            cl.indexed.put(entry.getKey(), clonedInner);
        }
        cl.indexedCount = this.indexedCount;
        // Clone non-indexed beliefs
        for (Map.Entry<Atom, Map<PredicateIndicator, LinkedList<Literal>>> nsEntry : this.nameSpaces.entrySet()) {
            Map<PredicateIndicator, LinkedList<Literal>> clonedNs = new HashMap<>();
            for (Map.Entry<PredicateIndicator, LinkedList<Literal>> piEntry : nsEntry.getValue().entrySet()) {
                LinkedList<Literal> clonedList = new LinkedList<>();
                for (Literal l : piEntry.getValue()) {
                    clonedList.add(l.copy());
                }
                clonedNs.put(piEntry.getKey(), clonedList);
            }
            cl.nameSpaces.put(nsEntry.getKey(), clonedNs);
        }
        cl.otherCount = this.otherCount;
        cl.nsProps.putAll(this.nsProps);
        return cl;
    }

    // ===================== DOM (Mind Inspector) =====================

    @Override
    public Element getAsDOM(Document document) {
        Element eDOMbels = document.createElement("beliefs");
        int tries = 0;
        while (tries < 10) {
            try {
                // Declare namespaces
                Element enss = document.createElement("namespaces");
                Element ens = document.createElement("namespace");
                ens.setAttribute("id", Literal.DefaultNS.toString());
                enss.appendChild(ens);
                for (Atom ns : getNameSpaces()) {
                    if (ns == Literal.DefaultNS) continue;
                    ens = document.createElement("namespace");
                    ens.setAttribute("id", ns.getFunctor());
                    enss.appendChild(ens);
                }
                eDOMbels.appendChild(enss);

                // Indexed beliefs — snapshot each inner map
                for (Map.Entry<String, Map<String, Literal>> entry : indexed.entrySet()) {
                    List<Literal> sorted = new ArrayList<>(entry.getValue().values());
                    Collections.sort(sorted);
                    for (Literal l : sorted) {
                        eDOMbels.appendChild(l.getAsDOM(document));
                    }
                }

                // Non-indexed beliefs
                for (Atom ns : nameSpaces.keySet()) {
                    Map<PredicateIndicator, LinkedList<Literal>> pis = nameSpaces.get(ns);
                    if (pis == null) continue;
                    List<PredicateIndicator> allPI = new ArrayList<>(pis.keySet());
                    Collections.sort(allPI);
                    for (PredicateIndicator pi : allPI) {
                        LinkedList<Literal> list = pis.get(pi);
                        if (list == null) continue;
                        for (Literal l : new ArrayList<>(list)) {
                            eDOMbels.appendChild(l.getAsDOM(document));
                        }
                    }
                }

                break; // success
            } catch (ConcurrentModificationException e) {
                tries++;
                // Clear children and retry
                while (eDOMbels.hasChildNodes()) {
                    eDOMbels.removeChild(eDOMbels.getLastChild());
                }
            }
        }
        return eDOMbels;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("HashMapBB[");
        sb.append("indexed: ");
        for (Map.Entry<String, Map<String, Literal>> entry : indexed.entrySet()) {
            sb.append(entry.getKey()).append("(").append(entry.getValue().size()).append(") ");
        }
        sb.append("| other: ").append(otherCount);
        sb.append("]");
        return sb.toString();
    }
}
