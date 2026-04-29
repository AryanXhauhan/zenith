// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

/**
 * ZenithEscrow – Module 4: Web3 Settlement
 *
 * This contract acts as a trustless escrow for Zenith settlements.
 *
 * Flow:
 *  1. Backend calls deposit() locking funds on-chain.
 *  2. Once off-chain settlement is verified, backend calls release().
 *  3. If settlement fails, backend calls refund() returning funds to sender.
 *
 * Security:
 *  - Only the contract owner (Zenith backend wallet) can release or refund.
 *  - Re-entrancy guard on all state-changing functions.
 *  - Each escrow is identified by a unique bytes32 settlementId
 *    (maps to the Zenith transaction reference).
 */
contract ZenithEscrow {

    // ── State ───────────────────────────────────────────────────────────────

    address public immutable owner;

    enum EscrowStatus { NONE, DEPOSITED, RELEASED, REFUNDED }

    struct Escrow {
        address  payer;
        address  payee;
        uint256  amount;
        EscrowStatus status;
        uint256  createdAt;
        string   zenithReference;  // ZNT-xxxx reference from the off-chain ledger
    }

    mapping(bytes32 => Escrow) public escrows;

    // Re-entrancy guard
    uint256 private _guardStatus = 1;

    // ── Events ──────────────────────────────────────────────────────────────

    event EscrowDeposited(
        bytes32 indexed settlementId,
        address indexed payer,
        address indexed payee,
        uint256 amount,
        string  zenithReference
    );
    event EscrowReleased(bytes32 indexed settlementId, address indexed payee, uint256 amount);
    event EscrowRefunded(bytes32 indexed settlementId, address indexed payer, uint256 amount);

    // ── Modifiers ───────────────────────────────────────────────────────────

    modifier onlyOwner() {
        require(msg.sender == owner, "ZenithEscrow: caller is not owner");
        _;
    }

    modifier nonReentrant() {
        require(_guardStatus == 1, "ZenithEscrow: reentrant call");
        _guardStatus = 2;
        _;
        _guardStatus = 1;
    }

    // ── Constructor ─────────────────────────────────────────────────────────

    constructor() {
        owner = msg.sender;
    }

    // ── External Functions ──────────────────────────────────────────────────

    /**
     * Deposit funds into escrow for a settlement.
     *
     * @param settlementId    Unique identifier (keccak256 of zenithReference recommended)
     * @param payee           The recipient address upon release
     * @param zenithReference The off-chain Zenith transaction reference (e.g. ZNT-12345-ABCD)
     */
    function deposit(
        bytes32 settlementId,
        address payee,
        string calldata zenithReference
    ) external payable nonReentrant {
        require(msg.value > 0,                        "ZenithEscrow: zero deposit");
        require(payee != address(0),                  "ZenithEscrow: invalid payee");
        require(escrows[settlementId].status == EscrowStatus.NONE, "ZenithEscrow: already exists");

        escrows[settlementId] = Escrow({
            payer:            msg.sender,
            payee:            payee,
            amount:           msg.value,
            status:           EscrowStatus.DEPOSITED,
            createdAt:        block.timestamp,
            zenithReference:  zenithReference
        });

        emit EscrowDeposited(settlementId, msg.sender, payee, msg.value, zenithReference);
    }

    /**
     * Release escrowed funds to the payee.
     * Called by Zenith backend once the off-chain ledger is confirmed.
     */
    function release(bytes32 settlementId) external onlyOwner nonReentrant {
        Escrow storage e = escrows[settlementId];
        require(e.status == EscrowStatus.DEPOSITED, "ZenithEscrow: not in DEPOSITED state");

        e.status = EscrowStatus.RELEASED;
        uint256 amount = e.amount;
        address payee  = e.payee;

        // Transfer after state update (Checks-Effects-Interactions pattern)
        (bool success, ) = payee.call{value: amount}("");
        require(success, "ZenithEscrow: transfer failed");

        emit EscrowReleased(settlementId, payee, amount);
    }

    /**
     * Refund escrowed funds to the payer.
     * Called if the off-chain settlement fails or is reversed.
     */
    function refund(bytes32 settlementId) external onlyOwner nonReentrant {
        Escrow storage e = escrows[settlementId];
        require(e.status == EscrowStatus.DEPOSITED, "ZenithEscrow: not in DEPOSITED state");

        e.status = EscrowStatus.REFUNDED;
        uint256 amount = e.amount;
        address payer  = e.payer;

        (bool success, ) = payer.call{value: amount}("");
        require(success, "ZenithEscrow: refund failed");

        emit EscrowRefunded(settlementId, payer, amount);
    }

    /**
     * View escrow details by settlementId.
     */
    function getEscrow(bytes32 settlementId) external view returns (Escrow memory) {
        return escrows[settlementId];
    }

    // Reject accidental ETH sends
    receive() external payable { revert("Use deposit()"); }
    fallback() external payable { revert("Use deposit()"); }
}
