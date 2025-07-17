package com.pse.pse.ui.fragments

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.pse.pse.R
import com.pse.pse.databinding.FragmentTeamLevelsBinding
import com.pse.pse.ui.viewModels.TeamViewModel
import com.pse.pse.utils.SharedPrefManager
import com.pse.pse.adapters.TeamLevelAdapter

class TeamLevelsFragment : BaseFragment() {

    // ─── ViewBinding ──────────────────────────────────────────────────────────
    private var _binding: FragmentTeamLevelsBinding? = null
    private val binding get() = _binding!!

    private val teamViewModel: TeamViewModel by viewModels()
    private lateinit var teamLevelAdapter: TeamLevelAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTeamLevelsBinding.inflate(inflater, container, false)
        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDrawerTrigger(view)
        showLoading()

        // ——— profile nav ———
        binding.profileIcon.setOnClickListener {
            findNavController().navigate(R.id.profileFragment)
        }

        // ——— RecyclerView & adapter ———
        teamLevelAdapter = TeamLevelAdapter { model ->
            val bundle = Bundle().apply {
                putInt("level", model.level)
                putParcelableArrayList(
                    "users",
                    ArrayList(model.users)
                )        // Parcelable list → new fragment
            }
            findNavController().navigate(R.id.levelUsersFragment, bundle)
        }

        binding.levelsRV.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = teamLevelAdapter
        }

        // ——— kick-off load ———
        val userId = SharedPrefManager(requireContext()).getId()
        /*if (userId != null) {
            teamViewModel.loadEverything(userId)
        }
*/
        // ——— observe combined list (stats + lock state) ———
        teamViewModel.teamLevelsWithStats.observe(viewLifecycleOwner) { list ->
            teamLevelAdapter.submitList(list)
            hideLoading()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
