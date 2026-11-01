-- New writes must explicitly identify their source; existing rows remain unchanged.
ALTER TABLE trade ALTER COLUMN source DROP DEFAULT;
