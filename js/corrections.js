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
    function walk(node) {
      if (!node || typeof node !== 'object') return;
      var insts = node['institutions'];
      if (insts && typeof insts === 'object') {
        Object.keys(insts).forEach(function (band) {
          var v = insts[band];
          if (Array.isArray(v)) v.forEach(fixInst);
        });
      }
    }
    walk(data['pool']); walk(data['resolved']);
    if (changed && window.console) console.info('[bryc] cost corrections applied:', touched.join(', '));
    return { changed: changed, touched: touched };
  };
})();
