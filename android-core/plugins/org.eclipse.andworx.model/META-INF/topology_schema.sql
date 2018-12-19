create table TABLEMODELTYPES ( id integer primary key auto_increment, node_id integer, modelType text, description text);
create table TABLENODES ( _id integer primary key auto_increment, _parent_id integer, name text, title text, model_id integer, level integer, FOREIGN KEY(model_id) REFERENCES tableModelTypes(id));
create table TABLEMODULES ( id integer primary key auto_increment, name text, location text, model_type_id integer, FOREIGN KEY(model_type_id) REFERENCES tableModelTypes(id));
create table TABLEREPOSITORIES ( id integer primary key auto_increment, name text, url text, model_type_id integer, FOREIGN KEY(model_type_id) REFERENCES tableModelTypes(id));
--create view all_nodes as select n._id as _id, n.name as name, n.title as title, m.name as model from nodes n inner join child_nodes c on c._child = n._id inner join models m on m._id = n.model;
--create table categories ( _id integer primary key auto_increment, node_id integer not null, description text not null, created text not null, creator text not null, modified text, modifier text, identifier text not null);
--create table folders ( _id integer primary key auto_increment, node_id integer not null, description text not null, created text not null, creator text not null, modified text, modifier text, identifier text not null,
--                       location text, hasDispositionSchedule integer not null, dispositionInstructions text, dispositionAuthority text);
