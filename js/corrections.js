/* Cost corrections applied to the engine's records before the page renders.
 *
 * The engine's cost lines are a faithful copy of louisiana_programs_with_embeddings.csv. That file carried a
 * room-and-board figure for schools with no dorms -- Franciscan Missionaries of Our Lady rendered "$1,200
 * Room & board", which is its BOOKS figure duplicated into the housing column. The file is fixed at source,
 * but pools generated before the fix still carry the old numbers and regeneration is the engine's to run.
 *
 * Policy: a school with no on-campus housing shows NO living line. Students are assumed to live at home, so
 * an off-campus allowance is not substituted -- a number the student will not pay is the same defect the
 * other way round. Removing the line lowers the total and the net by exactly the amount removed.
 *
 * Runs on the decoded transit data before main.js is appended, so it needs nothing from the compiled bundle.
 */
(function () {
  window.__brycApplyCorrections = function (data, corrections) {
    if (!data || !corrections) return { changed: 0 };
    var noHouse = {};
    (corrections.no_on_campus_housing || []).forEach(function (n) { noHouse[n] = true; });
    var changed = 0, touched = [];
    function fixInst(i) {
      if (!i || typeof i !== 'object') return;
      var name = (i['institution-name'] || i['name'] || '').trim().toLowerCase();
      var c = i['costs'];
      if (!name || !c || !noHouse[name]) return;
      var living = Number(c['living']) || 0;
      if (!living) return;
      delete c['living']; delete c['living-label'];
      if (typeof c['coa'] === 'number') c['coa'] = c['coa'] - living;
      if (typeof c['net'] === 'number') c['net'] = Math.max(0, c['net'] - living);
      changed++; touched.push(name + ' -−$' + living);
    }
    // Programme links. 363 of 1,431 engine programmes carry no program-url, and engine records carry
    // unitid: null on every one of them, so the institution NAME is the only handle. College Scorecard is
    // keyed by the same UNITID we already hold and its Fields of Study section covers this exact CIP —
    // the same fallback build_record uses for records we inject.
    var byName = corrections.unitid_by_name || {};
    var linkBase = corrections.program_link_base || 'https://collegescorecard.ed.gov/school/?';
    var linked = 0;
    function fixLinks(i) {
      if (!i || typeof i !== 'object') return;
      var progs = i['programs'];
      if (!Array.isArray(progs) || !progs.length) return;
      var uid = i['unitid'] || byName[((i['institution-name'] || i['name'] || '').trim().toLowerCase())];
      if (!uid) return;
      progs.forEach(function (p) {
        if (p && typeof p === 'object' && !p['program-url']) {
          p['program-url'] = linkBase + uid + '#fields-of-study';
          linked++;
        }
      });
    }
    function walk(node) {
      if (!node || typeof node !== 'object') return;
      var insts = node['institutions'];
      if (insts && typeof insts === 'object') {
        Object.keys(insts).forEach(function (band) {
          var v = insts[band];
          if (Array.isArray(v)) v.forEach(function (i) { fixInst(i); fixLinks(i); });
        });
      }
    }
    walk(data['pool']); walk(data['resolved']);

    /* TOPS. The engine leaves costs.tops = 0 on Louisiana institutions for students who demonstrably
     * receive TOPS at other schools in the same recommendation -- row26 showed TOPS at Southeastern,
     * McNeese, Nicholls and BRCC but nothing at LSU, Southern, ULM, UNO, LSUA or LA Tech. That understates
     * aid and overstates net cost, and it silently pushes a student away from the schools it is missing on.
     *
     * Amounts are the official LOSFA 2025-26 award-calculation tables (see tops_source). The student's award
     * level is not in the record -- costs['tops-name'] is an institution-level label, identical on nearly
     * every page -- so it is inferred from the amounts the engine DID populate. Two guards keep that
     * inference honest: at two-year schools TOPS Tech and TOPS Opportunity pay the same, so a level known
     * only from a two-year award is never used to fill a four-year school, and TOPS Tech never pays at a
     * four-year school at all. A student with no TOPS anywhere is left untouched -- absence of an award is
     * not evidence of eligibility, and inventing one would be the same defect in the other direction.
     */
    /* The student's real award, from the app's own student/tops-award field. The engine ignores it --
     * it applies the Opportunity rate at every four-year school and the Tech rate at every two-year one,
     * whatever the student actually holds. That convention is left alone so a page stays internally
     * consistent, but it gates what may be FILLED: TOPS Tech does not pay at a four-year school, and a
     * student recorded as 'none' gets nothing. Without this gate, inference from the engine's own numbers
     * reads every student as Opportunity and hands four-year awards to Tech students. */
    var awardBy = corrections.tops_award_by_student || {};
    function studentAward() {
      try {
        var t = (new URLSearchParams(location.search)).get('token');
        if (!t) return null;
        var seg = t.split('.')[1]; if (!seg) return null;
        seg = seg.replace(/-/g, '+').replace(/_/g, '/');
        while (seg.length % 4) seg += '=';
        var sid = (JSON.parse(atob(seg)) || {})['student-id'];
        return sid ? (awardBy[sid] || null) : null;
      } catch (e) { return null; }
    }
    var award = studentAward();

    var losfa = corrections.tops_losfa || {};
    var twoYear = {}; (corrections.tops_two_year || []).forEach(function (n) { twoYear[n] = true; });
    var topsAlias = corrections.tops_aliases || {};
    function normName(s) { return (s || '').toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim(); }
    var losfaByNorm = {}; Object.keys(losfa).forEach(function (k) { losfaByNorm[normName(k)] = k; });
    function losfaKey(name) { var n = normName(name); return losfaByNorm[n] || topsAlias[n] || null; }

    var all = [];
    function collect(node) {
      if (!node || typeof node !== 'object') return;
      var insts = node['institutions'];
      if (insts && typeof insts === 'object') {
        Object.keys(insts).forEach(function (band) {
          var v = insts[band];
          if (Array.isArray(v)) v.forEach(function (i) { if (i && typeof i === 'object') all.push(i); });
        });
      }
    }
    collect(data['pool']); collect(data['resolved']);

    var score = {}, sawFourYear = false, observed = 0;
    all.forEach(function (i) {
      var c = i['costs']; if (!c) return;
      var amt = Number(c['tops']) || 0; if (!amt) return;
      var key = losfaKey(i['institution-name'] || i['name']); if (!key) return;
      observed++;
      var levels = losfa[key];
      Object.keys(levels).forEach(function (lvl) {
        if (Math.abs(levels[lvl] - amt) < 1.0) {
          score[lvl] = (score[lvl] || 0) + 1;
          if (!twoYear[key]) sawFourYear = true;
        }
      });
    });
    var level = null, best = 0;
    Object.keys(score).forEach(function (l) { if (score[l] > best) { best = score[l]; level = l; } });

    var topsFilled = 0, topsTotal = 0;
    if (award === 'none') level = null;          // recorded as not TOPS-eligible
    if (level) {
      all.forEach(function (i) {
        var c = i['costs']; if (!c) return;
        if (Number(c['tops']) > 0) return;
        var key = losfaKey(i['institution-name'] || i['name']); if (!key) return;
        var levels = losfa[key], use;
        if (twoYear[key]) {
          use = levels[level] ? level : (c['tops-name'] === 'TOPS-Tech' ? 'tops-tech' : 'tops-opportunity');
        } else {
          if (award === 'tech') return;          // TOPS Tech does not pay at a four-year school
          if (!award && !sawFourYear) return;    // no recorded award, level known only from a two-year one
          if (level === 'tops-tech') return;
          use = level;
        }
        var amt = levels[use]; if (!amt) return;
        var coa = Number(c['coa']) || 0, pell = Number(c['pell']) || 0;
        c['tops'] = Math.round(amt);
        c['aid-covered'] = Math.round(pell + amt);
        c['net'] = coa ? Math.max(0, Math.round(coa - pell - amt))
                       : Math.max(0, Math.round((Number(c['net']) || 0) - amt));
        topsFilled++; topsTotal += amt;
      });
    }

    if (window.console && (changed || linked || topsFilled)) {
      console.info('[bryc] corrections: ' + changed + ' cost, ' + linked + ' programme link(s) filled, '
        + topsFilled + ' TOPS award(s) filled'
        + (level ? ' at ' + level + ' (' + observed + ' observed)' : '')
        + (award ? ' [award: ' + award + ']' : ' [award: unrecorded]'));
    }
    return { changed: changed, linked: linked, touched: touched,
             topsFilled: topsFilled, topsLevel: level, topsTotal: topsTotal };
  };
})();
