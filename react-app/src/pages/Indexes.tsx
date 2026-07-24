import { useMemo, useState } from "react";
import { Alert, Card, Form } from "react-bootstrap";
import { useGetIndexMembersQuery, useGetIndexesQuery } from "../functions/api";
import { IndexMembersTable } from "../components/IndexMembersTable";
import { Fattore1000Russell1000CompareTable } from "../components/Fattore1000Russell1000CompareTable";

export default function Indexes() {
  const {
    data: indexes,
    isLoading: indexesLoading,
    error: indexesError,
  } = useGetIndexesQuery();

  const [selectedCode, setSelectedCode] = useState<string>("");

  // Until the user picks an index, default to FAT50 (or the first index loaded)
  const defaultCode =
    indexes?.find((i) => i.code === "FAT50")?.code ?? indexes?.[0]?.code ?? "";
  const effectiveCode = selectedCode || defaultCode;

  const shouldFetchMembers = effectiveCode.trim().length > 0;
  const {
    data: members,
    isLoading: membersLoading,
    error: membersError,
  } = useGetIndexMembersQuery(effectiveCode, { skip: !shouldFetchMembers });

  const selectedLabel = useMemo(() => {
    if (!indexes) return effectiveCode;
    const mi = indexes.find((i) => i.code === effectiveCode);
    return mi ? `${mi.displayName} (${mi.code})` : effectiveCode;
  }, [indexes, effectiveCode]);

  return (
    <div className="p-3">
      <h2>Indexes</h2>

      <Card className="p-3">
        <h5>Select index</h5>
        <Form.Group className="mb-2">
          <Form.Label>Index</Form.Label>
          <Form.Select
            value={effectiveCode}
            onChange={(e) => setSelectedCode(e.target.value)}
            disabled={indexesLoading || !indexes || indexes.length === 0}
            style={{ maxWidth: 420 }}
          >
            {(indexes || []).map((i) => (
              <option key={i.code} value={i.code}>
                {i.displayName} ({i.code})
              </option>
            ))}
          </Form.Select>
        </Form.Group>

        {!!indexesError && (
          <Alert variant="danger">
            Failed to load indexes: {JSON.stringify(indexesError)}
          </Alert>
        )}

        <h5 className="mt-3">{selectedLabel}</h5>
        <IndexMembersTable
          members={members || []}
          isLoading={membersLoading}
          errors={[membersError]}
        />

        {effectiveCode === "FAT1000" && (
          <Fattore1000Russell1000CompareTable
            members={members || []}
            membersLoading={membersLoading}
            membersError={membersError}
          />
        )}
      </Card>
    </div>
  );
}
