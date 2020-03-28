const express = require('express');
const path = require('path');
const fs = require('fs');
const csv = require('csv-parser');
const stripBom = require('strip-bom-stream');
const pgStructure = require('pg-structure').default;

function splitComment(comment) {
  const res = {comment_omop: '', comment_aphp: ''};
  if (!comment) {
    return res;
  }
  const splitComment = comment.split('- APHP Annotation :');
  res.comment_omop = splitComment[0].replace('OMOP Comment : ', '');
  if (splitComment.length == 2) {
    res.comment_aphp = splitComment[1];
  }
  return res;
}

function nameSorter(a, b) {
  if (a.name < b.name) return -1;
  if (a.name > b.name) return 1;
  return 0;
}

const tablesData = [];
const links = [];
async function loadDbSchema() {
  const db = await pgStructure({ database: 'postgres', user: 'postgres', password: 'password' }, {  });
  const tablesRef = {}

  let id = 0;
  for (let i = 0; i < db.tables.length; i++) {
    const t = db.tables[i];

    const rowKey = 'row-' + i;
    
    const columns = [];
    for (let j = 0; j < t.columns.length; j++) {
      const colKey = rowKey + '-col-' + j;
      const c = t.columns[j];
      columns.push({
        id: id.toString(),
        _key: colKey,
        name: c.name,
        type: c.type.shortName,
        ...splitComment(c.comment)
      });
      columns.sort(nameSorter);
      id += 1;
    }
    
    const table = {
      id: id.toString(),
      _key: rowKey,
      name: t.name,
      ...splitComment(t.comment),
      columns
    }
    tablesData.push(table);
    tablesRef[t.name] = table.id;
    id += 1;
  }
  tablesData.sort(nameSorter);

  function addTarget(l, relation) {
    const sourceTableId = tablesRef[relation.sourceTable.name];
    const targetTableId = tablesRef[relation.targetTable.name];
    // Do not add a link if it already exists in the other way
    if (l.hasOwnProperty(targetTableId) && l[targetTableId].has(sourceTableId)) {
      return;
    }

    if (!l.hasOwnProperty(sourceTableId)) {
      l[sourceTableId] = new Set();
    }
    l[sourceTableId].add(targetTableId);
  }

  const linkSet = {}
  for (const table of db.tables) {
    for (relation of table.o2mRelations) {
      addTarget(linkSet, relation);
    }
    // This is already taken into account with o2m and m2o relationships
    // for (relation of table.m2mRelations) {
    //   addTarget(linkSet, relation);
    // }
    for (relation of table.m2oRelations) {
      addTarget(linkSet, relation);
    }
  }
  for (const source in linkSet) {
    for (const target of linkSet[source]) {
      links.push({source, target});
    }
  }
}
loadDbSchema();


const app = express();
app.use(express.static(path.join(__dirname, 'build')));

app.get('/schema', function (req, res) {
 return res.json({tables: tablesData, links});
});
 
app.get('/', function (req, res) {
  res.sendFile(path.join(__dirname, 'build', 'index.html'));
});

const port = process.env.PORT || 8080;
console.log('server running on port: ' + port);
app.listen(port); 