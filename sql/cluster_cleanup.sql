update cluster_record cr set is_deleted = true where not exists ( select * from bib_record where contributes_to = cr.id );
