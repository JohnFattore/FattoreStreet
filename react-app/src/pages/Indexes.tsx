import { useMemo, useState } from "react";
import { Alert, Card, Form } from "react-bootstrap";
import { useGetIndexMembersQuery, useGetIndexesQuery } from "../functions/api";
import { IndexMembersTable } from "../components/IndexMembersTable";
import { Fattore1000Russell1000CompareTable } from "../components/Fattore1000Russell1000CompareTable";

// Dropdown entry for the Fattore 1000 vs Russell 1000 comparison. It is not an
// index code: picking it fetches FAT1000 members and shows only the comparison.
const RUSSELL_COMPARE = "FAT1000_VS_R1000";

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
  const isCompare = effectiveCode === RUSSELL_COMPARE;
  const membersCode = isCompare ? "FAT1000" : effectiveCode;

  const shouldFetchMembers = membersCode.trim().length > 0;
  const {
    data: members,
    isLoading: membersLoading,
    error: membersError,
  } = useGetIndexMembersQuery(membersCode, { skip: !shouldFetchMembers });

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
            {indexes?.some((i) => i.code === "FAT1000") && (
              <option value={RUSSELL_COMPARE}>
                Fattore 1000 vs Russell 1000 (IWB)
              </option>
            )}
          </Form.Select>
        </Form.Group>

        {!!indexesError && (
          <Alert variant="danger">
            Failed to load indexes: {JSON.stringify(indexesError)}
          </Alert>
        )}

        {isCompare ? (
          <Fattore1000Russell1000CompareTable
            members={members || []}
            membersLoading={membersLoading}
            membersError={membersError}
          />
        ) : (
          <>
            <h5 className="mt-3">{selectedLabel}</h5>
            <IndexMembersTable
              members={members || []}
              isLoading={membersLoading}
              errors={[membersError]}
            />
          </>
        )}
      </Card>
    </div>
  );
}
