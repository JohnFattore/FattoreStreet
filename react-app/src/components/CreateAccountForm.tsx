import { Form, Col, Row, Alert } from "react-bootstrap";
import { useForm, SubmitHandler } from "react-hook-form";
import { useSelector } from "react-redux";
import { RootState } from "../main";
import { useCreateAccountMutation } from "../functions/api";
import LoadingButton from "./LoadingButton";
import { getErrorMessages } from "../functions/helperFunctions";

interface IFormInput {
    name: string;
    accountType: string;
}

export default function CreateAccountForm() {
    const [createAccount, { error, isLoading, isSuccess }] = useCreateAccountMutation();
    const { access } = useSelector((state: RootState) => state.user);

    const {
        register,
        handleSubmit,
        formState: { errors },
        reset
    } = useForm<IFormInput>();

    const onSubmit: SubmitHandler<IFormInput> = async (data) => {
        await createAccount({
            name: data.name,
            account_type: data.accountType
        });
        reset();
    };

    if (!access) {
        return null;
    }

    return (
        <>
            <h3>Create Account</h3>
            {error && <Alert variant="danger">{getErrorMessages(error["data"])}</Alert>}
            {isSuccess && <Alert variant="success">Account created successfully!</Alert>}
            <Form onSubmit={handleSubmit(onSubmit)}>
                <Row>
                    <Col>
                        <Form.Control
                            size="lg"
                            {...register("name", {
                                required: true,
                            })}
                            placeholder="Account Name"
                        />
                        {errors.name && (
                            <Alert variant="danger" role="nameError">
                                Error: Account name is required
                            </Alert>
                        )}
                    </Col>
                    <Col>
                        <Form.Select
                            size="lg"
                            {...register("accountType", { required: true })}
                        >
                            <option value="OTHER">Other</option>
                            <option value="ROTH_IRA">Roth IRA</option>
                            <option value="TRADITIONAL_IRA">Traditional IRA</option>
                            <option value="TAXABLE_ACCOUNT">Taxable Account</option>
                            <option value="ROTH_401K">Roth 401k</option>
                            <option value="TRADITIONAL_401K">Traditional 401k</option>
                        </Form.Select>
                        {errors.accountType && (
                            <Alert variant="danger" role="accountTypeError">
                                Error: Account type is required
                            </Alert>
                        )}
                    </Col>
                </Row>
                <div className="mt-2">
                    <LoadingButton label={"Create Account"} loading={isLoading} />
                </div>
            </Form>
        </>
    );
}
