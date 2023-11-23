// ============================================================================
// Copyright BRAINTRIBE TECHNOLOGY GMBH, Austria, 2002-2022
// 
// This library is free software; you can redistribute it and/or modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either version 3 of the License, or (at your option) any later version.
// 
// This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
// 
// You should have received a copy of the GNU Lesser General Public License along with this library; See http://www.gnu.org/licenses/.
// ============================================================================
package com.braintribe.model.processing.jdbc.support.service.expert;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import com.braintribe.exception.Exceptions;
import com.braintribe.logging.Logger;
import com.braintribe.utils.IOTools;

public class PostgresqlExpert implements DatabaseExpert {

	private final static Logger logger = Logger.getLogger(PostgresqlExpert.class);

	@Override
	public String createMissingIndices(Connection connection, boolean dryMode) {

		StringBuilder sb = new StringBuilder();
		if (dryMode) {
			sb.append("Dry mode: on (no actions will be taken)\n\n");
		} else {
			sb.append("Dry mode: off (below statements will be executed)\n\n");
		}

		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			//@formatter:off
			ps = connection.prepareStatement("SELECT 'CREATE INDEX fk_' || conname || '_idx ON ' \n" + 
					"       || relname || ' ' || \n" + 
					"       regexp_replace(\n" + 
					"           regexp_replace(pg_get_constraintdef(pg_constraint.oid, true), \n" + 
					"           ' REFERENCES.*$','',''), 'FOREIGN KEY ','','') || ';'\n" + 
					"FROM pg_constraint \n" + 
					"JOIN pg_class \n" + 
					"    ON (conrelid = pg_class.oid)\n" + 
					"JOIN pg_namespace\n" + 
					"    ON (relnamespace = pg_namespace.oid)\n" + 
					"WHERE contype = 'f'\n" + 
					"  AND nspname = 'public'\n" + 
					"  AND NOT EXISTS (\n" + 
					"  SELECT * FROM pg_class pgc\n" + 
					"    JOIN pg_namespace pgn ON (pgc.relnamespace = pgn.oid)\n" + 
					"  WHERE relkind='i'\n" + 
					"    AND pgc.relname = ('fk_' || conname || '_idx') );");
			//@formatter:on

			rs = ps.executeQuery();

			List<String> createIndexStatements = new ArrayList<>();

			while (rs.next()) {
				String createIndex = rs.getString(1);
				sb.append(createIndex + "\n");
				if (!dryMode) {
					createIndexStatements.add(createIndex);
				}
			}

			if (!createIndexStatements.isEmpty() && !dryMode) {
				createIndices(connection, createIndexStatements, sb);
			}

		} catch (Exception e) {
			throw Exceptions.unchecked(e);
		} finally {
			IOTools.closeCloseable(rs, logger);
			IOTools.closeCloseable(ps, logger);
		}
		return sb.toString();
	}

	private void createIndices(Connection con, List<String> createIndexStatements, StringBuilder sb) {
		for (String ci : createIndexStatements) {
			sb.append("\n-- Executing " + ci + "\n");
			PreparedStatement st = null;
			try {
				st = con.prepareStatement(ci);
				st.execute();
			} catch (Exception e) {
				sb.append(Exceptions.stringify(e) + "\n");
			} finally {
				IOTools.closeCloseable(st, logger);
			}
		}
	}

}
