import { Form, Col, Row, Alert, Button, Modal } from "react-bootstrap";
import { useForm, SubmitHandler } from "react-hook-form";
import { useSelector } from "react-redux";
import { RootState } from "../main";
import { useCreateAccountMutation } from "../functions/api";
import LoadingButton from "./LoadingButton";
import { getErrorMessages } from "../functions/helperFunctions";
import { useState } from "react";
import { FetchBaseQueryError } from "@reduxjs/toolkit/query";

interface IFormInput {
    name: string;
    accountType: string;
}

export default function CreateAccountForm() {
    const [createAccount, { error, isLoading }] = useCreateAccountMutation();
    const { access } = useSelector((state: RootState) => state.user);
    const [show, setShow] = useState(false);

    const handleClose = () => {
        setShow(false);
        reset();
    };
    const handleShow = () => setShow(true);

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
        setShow(false);
        reset();
    };

    if (!access) {
        return null;
    }

    return (
        <>
            <Button variant="primary" onClick={handleShow} className="mb-3">
                Create New Account
            </Button>

            <Modal show={show} onHide={handleClose} dialogClassName="create-account-modal">
                <Modal.Header closeButton>
                    <Modal.Title>Create Account</Modal.Title>
                </Modal.Header>
                <Modal.Body>
                    {error ? (
                        <Alert variant="danger">
                            {getErrorMessages(
                                typeof error === "object" &&
                                    error !== null &&
                                    "data" in error
                                    ? (error as FetchBaseQueryError).data
                                    : undefined
                            )}
                        </Alert>
                    ) : null}
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
                        </Row>
                        <Row>
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
                        <div>
                            <Button variant="secondary" onClick={handleClose}>
                                Close
                            </Button>
                            <LoadingButton label={"Create Account"} loading={isLoading} />
                        </div>
                    </Form>
                </Modal.Body>
            </Modal>
        </>
    );
}
