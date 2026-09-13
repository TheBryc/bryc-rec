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
    if (window.console && (changed || linked)) {
      console.info('[bryc] corrections: ' + changed + ' cost, ' + linked + ' programme link(s) filled');
    }
    return { changed: changed, linked: linked, touched: touched };
  };
})();
